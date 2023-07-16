package com.kohlschutter.junixsocket.selftest.android;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.kohlschutter.junixsocket.selftest.android.databinding.ActivityScrollingBinding;

import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.selftest.Selftest;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.CompletableFuture;

public class ScrollingActivity extends AppCompatActivity {

    private ActivityScrollingBinding binding;

    private static final class TextViewWriter extends Writer {

        private final NestedScrollView sv;
        private final TextView tv;

        TextViewWriter(NestedScrollView sv, TextView tv, boolean clear) {
            this.sv = sv;
            this.tv = tv;
            sv.setOverScrollMode(NestedScrollView.OVER_SCROLL_ALWAYS);

            if (clear) {
                tv.post(() -> tv.setText(""));
                flush();
            }
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            String s = new String(cbuf, off, len);
            tv.post(() -> tv.append(s));
        }

        @Override
        public void flush() {
//            sv.post(() -> {
//                sv.fullScroll(View.FOCUS_DOWN);
//            });
        }

        @Override
        public void close() {
            flush();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        System.out.println("junixsocket supported: "+AFSocket.isSupported());
        binding = ActivityScrollingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        CollapsingToolbarLayout toolBarLayout = binding.toolbarLayout;
        toolBarLayout.setTitle(getTitle());
        binding.selftestText.selftestScrollView.fullScroll(View.FOCUS_DOWN);

        NestedScrollView scrollView = binding.selftestText.selftestScrollView;
        TextView textView = binding.selftestText.selftestText;

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    Selftest.runSelftest(new TextViewWriter(scrollView, textView, true));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        FloatingActionButton fab = binding.fab;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Not yet implemented", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}