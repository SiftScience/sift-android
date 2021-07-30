package siftscience.android.hellosift;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import siftscience.android.Sift;

public class OtherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other);

        Sift.open(this);
        Sift.collect();
        Button buttonCollect = findViewById(R.id.buttonCollect);
        Button buttonUpload = findViewById(R.id.buttonUpload);
        buttonCollect.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                Sift.collect();
            }
        });
        buttonUpload.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                Sift.upload();
            }
        });
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
