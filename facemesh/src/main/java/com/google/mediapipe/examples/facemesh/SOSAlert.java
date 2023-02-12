package com.google.mediapipe.examples.facemesh;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class SOSAlert extends AppCompatActivity {
EditText fullName,mobile,fmobile,altfmobile,email;
DBHelper db;
Button signup;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sosalert);
        fullName = findViewById(R.id.fullName);
        mobile = findViewById(R.id.phoneNumber);
//        email = findViewById(R.i);
        fmobile = findViewById(R.id.friendNumber2);
        altfmobile = findViewById(R.id.friendNumber);
        signup = findViewById(R.id.signUp);
        db = new DBHelper(this);
        signup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //save karna hai
                String fname = fullName.getText().toString();
                String mob = mobile.getText().toString();
                String fmob = fmobile.getText().toString();
                String altfmob = fmobile.getText().toString();

                Intent intent = new Intent(SOSAlert.this,MainActivity.class);
                intent.putExtra("num",fmob);
                intent.putExtra("fnum",altfmob);
                startActivity(intent);
            }
        });
    }


}