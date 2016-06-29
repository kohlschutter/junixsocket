/**
 * junixsocket
 *
 * Copyright (c) 2009,2014 Christian Kohlschütter
 *
 * The author licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.newsclub.net.unix.benchamrks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

//to run this benchmark use 
//while true; do socat UNIX:/tmp/junixsocket-test.sock /dev/zero 2> /dev/null; done
@BenchmarkMode(Mode.AverageTime)
@Fork(3)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class AFUNIXSocketBenchmarkRead {

	private byte[] bytes;
	private InputStream is;
	private Socket sock;

	@Param({ "1", "100" })
	public int payload;
	private AFUNIXServerSocket server;

	@Setup
	public void setup() throws IOException {
		final File file = new File(new File(System.getProperty("java.io.tmpdir")),
				"junixsocket-test.sock");

		AFUNIXSocketAddress address = new AFUNIXSocketAddress(file);
		server = AFUNIXServerSocket.newInstance();
		server.bind(address);
		sock = server.accept();
		is = sock.getInputStream();
		bytes = new byte[payload];

	}

	@Benchmark
	public int read() throws IOException {
		return is.read(bytes);
	}

	@TearDown
	public void tearDown() throws IOException {
		is.close();
		sock.close();
		server.close();
	}

	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder().include(".*" + AFUNIXSocketBenchmarkRead.class.getSimpleName()
				+ ".*").build();

		new Runner(opt).run();
	}

}
