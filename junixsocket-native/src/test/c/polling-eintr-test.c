/*
 * junixsocket
 *
 * Copyright 2009-2026 Christian Kohlschütter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#if defined(_WIN32)

int main(void) {
    return 0;
}

#else

#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/wait.h>
#include <unistd.h>

#include "../../main/c/polling_with_deadline.h"

static volatile sig_atomic_t signalReceived = 0;

static void signalHandler(int signalNumber CK_UNUSED) {
    signalReceived = 1;
}

static void sleepMillis(long millis) {
    struct timespec delay = {
        .tv_sec = millis / 1000,
        .tv_nsec = (millis % 1000) * 1000000,
    };
    while(nanosleep(&delay, &delay) == -1 && errno == EINTR) {
    }
}

static int pipeWriteFd = -1;

static void writeAfterDeadline(void) {
    sleepMillis(2500);
    char value = 1;
    if(write(pipeWriteFd, &value, sizeof(value)) != sizeof(value)) {
        _exit(EXIT_FAILURE);
    }
}

static int waitForChild(pid_t child) {
    int status;
    while(waitpid(child, &status, 0) == -1) {
        if(errno != EINTR) {
            return -1;
        }
    }
    return WIFEXITED(status) && WEXITSTATUS(status) == EXIT_SUCCESS ? 0 : -1;
}

int main(void) {
    int pipeFds[2];
    if(pipe(pipeFds) == -1) {
        perror("pipe");
        return EXIT_FAILURE;
    }
    pipeWriteFd = pipeFds[1];

    struct sigaction action = {
        .sa_handler = signalHandler,
    };
    sigemptyset(&action.sa_mask);
    if(sigaction(SIGALRM, &action, NULL) == -1) {
        perror("sigaction");
        return EXIT_FAILURE;
    }

    pid_t writerChild = fork();
    if(writerChild == -1) {
        perror("fork");
        return EXIT_FAILURE;
    } else if(writerChild == 0) {
        writeAfterDeadline();
        _exit(EXIT_SUCCESS);
    }

    struct pollfd pollFd = {
        .fd = pipeFds[0],
        .events = POLLIN,
    };
    alarm(1);
    int ret = pollWithDeadline(&pollFd, 1, 2000);
    alarm(0);

    int childFailed = waitForChild(writerChild);
    close(pipeFds[0]);
    close(pipeFds[1]);

    if(childFailed != 0) {
        fprintf(stderr, "The writer child failed\n");
        return EXIT_FAILURE;
    }
    if(!signalReceived) {
        fprintf(stderr, "SIGALRM was not received\n");
        return EXIT_FAILURE;
    }
    if(ret != 0) {
        fprintf(stderr, "Expected timeout after EINTR, got poll result %d\n", ret);
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
}

#endif
