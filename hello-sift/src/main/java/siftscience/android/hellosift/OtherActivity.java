package siftscience.android.hellosift;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import siftscience.android.Sift;

public class OtherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other);

        Sift.open(this);
        Sift.collect();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Sift.pause();
    }

    protected void onResume() {
        super.onResume();
        Sift.resume(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Sift.close();
    }
}
