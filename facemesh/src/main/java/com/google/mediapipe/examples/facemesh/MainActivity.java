package com.google.mediapipe.examples.facemesh;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutioncore.VideoInput;
import com.google.mediapipe.solutions.facemesh.FaceMesh;
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions;
import com.google.mediapipe.solutions.facemesh.FaceMeshResult;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Main activity of MediaPipe Face Mesh app. */
public class MainActivity extends AppCompatActivity {
  private static final String TAG = "MainActivity";
  TextView textView, textView2;// This is used to show details on textView2.
  private FaceMesh facemesh;
  // Run the pipeline and the model inference on GPU or CPU.
  private static final boolean RUN_ON_GPU = true;

  private enum InputSource {
    //Here the input source is Camera
    UNKNOWN,
    VIDEO,
    CAMERA,
  }
  private InputSource inputSource = InputSource.UNKNOWN;
  // Image demo UI and image loader components.
  private VideoInput videoInput;
  private CameraInput cameraInput;

  private SolutionGlSurfaceView<FaceMeshResult> glSurfaceView;

  private SoundPool soundPool;
  private int sound1;

  FusedLocationProviderClient fusedLocationProviderClient;
  String country, city, address, longitude, latitude;
  private final static int REQUEST_CODE=100;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    //Applying sound system here
    AudioAttributes audioAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();

    soundPool = new SoundPool.Builder()
            .setMaxStreams(1).setAudioAttributes(audioAttributes)
                    .build();

    sound1 = soundPool.load(this,R.raw.finalsound,1);
    fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
    if (inputSource == InputSource.CAMERA) {
      return;
    }
    stopCurrentPipeline();
    setupStreamingModePipeline(InputSource.CAMERA);
  }

  //Getting the last known location of the device
  private void getLastLocation() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
      fusedLocationProviderClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
        @Override
        public void onSuccess(Location location) {
         if(location!=null)
         {
           Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
           try {
             List<Address> addresses = geocoder.getFromLocation(location.getLatitude(),location.getLongitude(),1);
             address = addresses.get(0).getAddressLine(0);
             city = addresses.get(0).getLocality();
             country =addresses.get(0).getCountryName();
           } catch (IOException e) {
             Log.i("Location error raise:",e.toString());
           }
         }
        }
      });
    }
    else
    {
      askPermission();
    }
  }

  //Asking for the location permissions
  private void askPermission(){
    ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_CODE);
  }
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if(requestCode==REQUEST_CODE){
      if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
      {
        getLastLocation();
      }
      else
      {
        Toast.makeText(MainActivity.this,"Required permission not given",Toast.LENGTH_LONG).show();
      }
    }
  }

  //This will play the sound
  private void playSound(){
    soundPool.play(sound1,1,1,0,0,1);
  }

  //This will pause the sound
  private void pauseSound(){
    soundPool.pause(sound1);
  }

  //This will send the SOS message to the registered no. along with the location
  private void sendSOS(){
    SmsManager smsManager = SmsManager.getDefault();
    getLastLocation();
    String s = "Urgent!! name is driving at "+" latitude: "+ latitude + " longitude: " +longitude+ "is not in safe state to drive. Call immediately.";
    smsManager.sendTextMessage("+916266019364",null,s,null,null);
    smsManager.sendTextMessage("+919318386024",null,s,null,null);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    soundPool.release();
    soundPool = null;
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (inputSource == InputSource.CAMERA) {
      // Restarts the camera and the opengl surface rendering.
      cameraInput = new CameraInput(this);
      cameraInput.setNewFrameListener(textureFrame -> facemesh.send(textureFrame));
      glSurfaceView.post(this::startCamera);
      glSurfaceView.setVisibility(View.VISIBLE);
    } else if (inputSource == InputSource.VIDEO) {
      videoInput.resume();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (inputSource == InputSource.CAMERA) {
      glSurfaceView.setVisibility(View.GONE);
      cameraInput.close();
    } else if (inputSource == InputSource.VIDEO) {
      videoInput.pause();
    }
  }

  /** Sets up core workflow for streaming mode. */
  //This will setup the streamingmodepipeline
  private void setupStreamingModePipeline(InputSource inputSource) {
    this.inputSource = inputSource;
    // Initializes a new MediaPipe Face Mesh solution instance in the streaming mode.
    facemesh =
        new FaceMesh(
            this,
            FaceMeshOptions.builder()
                .setStaticImageMode(false)
                .setRefineLandmarks(true)
                .setRunOnGpu(RUN_ON_GPU)
                .build());
    facemesh.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Face Mesh error:" + message));

    if (inputSource == InputSource.CAMERA) {
      cameraInput = new CameraInput(this);
      cameraInput.setNewFrameListener(textureFrame -> facemesh.send(textureFrame));
    } else if (inputSource == InputSource.VIDEO) {
      videoInput = new VideoInput(this);
      videoInput.setNewFrameListener(textureFrame -> facemesh.send(textureFrame));
    }

    // Initializes a new Gl surface view with a user-defined FaceMeshResultGlRenderer.
    glSurfaceView =
        new SolutionGlSurfaceView<>(this, facemesh.getGlContext(), facemesh.getGlMajorVersion());
    glSurfaceView.setSolutionResultRenderer(new FaceMeshResultGlRenderer());
    glSurfaceView.setRenderInputImage(true);
    facemesh.setResultListener(
        faceMeshResult -> {
          logNoseLandmark(faceMeshResult /*showPixelValues=*/);
          glSurfaceView.setRenderData(faceMeshResult);
          glSurfaceView.requestRender();
        });

    // The runnable to start camera after the gl surface view is attached.
    // For video input source, videoInput.start() will be called when the video uri is available.
    if (inputSource == InputSource.CAMERA) {
      glSurfaceView.post(this::startCamera);
    }

    // Updates the preview layout.
    FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
    frameLayout.removeAllViewsInLayout();
    frameLayout.addView(glSurfaceView);
    glSurfaceView.setVisibility(View.VISIBLE);
    frameLayout.requestLayout();
  }

  //This will start the front camera.
  private void startCamera() {
    cameraInput.start(
        this,
        facemesh.getGlContext(),
        CameraInput.CameraFacing.FRONT,
        glSurfaceView.getWidth(),
        glSurfaceView.getHeight());
  }

  //This will stop the current pipeline
  private void stopCurrentPipeline() {
    if (cameraInput != null) {
      cameraInput.setNewFrameListener(null);
      cameraInput.close();
    }
    if (videoInput != null) {
      videoInput.setNewFrameListener(null);
      videoInput.close();
    }
    if (glSurfaceView != null) {
      glSurfaceView.setVisibility(View.GONE);
    }
    if (facemesh != null) {
      facemesh.close();
    }
  }


  //This the main logic code to detect the various happenings of the faces like eyes, yawn etc.
  @SuppressLint("SetTextI18n")
  private double detectEyes1(FaceMeshResult result){
    //This method is used to detect left eye.
    double ry1 = result.multiFaceLandmarks().get(0).getLandmarkList().get(5).getY() * 1920;
    double ry2 = result.multiFaceLandmarks().get(0).getLandmarkList().get(4).getY() * 1920;
    double ay1 = result.multiFaceLandmarks().get(0).getLandmarkList().get(373).getY() * 1920;
    double ay2 = result.multiFaceLandmarks().get(0).getLandmarkList().get(386).getY() * 1920;

    return (ay1 - ay2) / (ry2 - ry1);
  }

  private double detectEyes2(FaceMeshResult result)
  {
    //This method is used to detect the right eye.
    double ry1 = result.multiFaceLandmarks().get(0).getLandmarkList().get(5).getY() * 1920;
    double ry2 = result.multiFaceLandmarks().get(0).getLandmarkList().get(4).getY() * 1920;
    double by1 = result.multiFaceLandmarks().get(0).getLandmarkList().get(158).getY() * 1920;
    double by2 = result.multiFaceLandmarks().get(0).getLandmarkList().get(145).getY()*1920;
    return (Math.abs(by1-by2)) / (Math.abs(ry2 - ry1));
  }

  private double detectYawning(FaceMeshResult result){
    //This method is used to detect the yawning
    double ry1 = result.multiFaceLandmarks().get(0).getLandmarkList().get(5).getY() * 1920;
    double ry2 = result.multiFaceLandmarks().get(0).getLandmarkList().get(4).getY() * 1920;
    double mUP = result.multiFaceLandmarks().get(0).getLandmarkList().get(11).getY()*1920;
    double mDOWN = result.multiFaceLandmarks().get(0).getLandmarkList().get(14).getY()*1920;
    return (Math.abs(mUP-mDOWN))/(Math.abs(ry1-ry2));
  }

  private double detectHeadDown(FaceMeshResult result){
    //This method is used to detect the head down.
    double ry1 = result.multiFaceLandmarks().get(0).getLandmarkList().get(5).getY()*1920;
    double ry2 = result.multiFaceLandmarks().get(0).getLandmarkList().get(4).getY() * 1920;
    double headUP = result.multiFaceLandmarks().get(0).getLandmarkList().get(101).getY()*1920;
    double headDOWN = result.multiFaceLandmarks().get(0).getLandmarkList().get(10).getY()*1920;
    return (Math.abs(headUP - headDOWN))/(ry2-ry1);
  }
  int Eyescount = 0;//This variable is used to detect total eye frames.
  int HeadCount = 0;
  private void overallAlgorithm(FaceMeshResult result){
    //This is the overall algorithm which detects the eyes, head and yawning together.
    double Eyes1 = detectEyes1(result);
    double Eyes2 = detectEyes2(result);
    double Head = detectHeadDown(result);

    if(Eyes1<0.6 && Eyes2<0.6)
    {Eyescount++;
      Log.i("Eyes count", String.valueOf(Eyescount++));}
    else
    {Eyescount=0;}

    if(Head < 7.85)
    {
      HeadCount++;
      Log.i("Headcount count", String.valueOf(HeadCount++));
    }
    else
    {
      HeadCount = 0;
    }
  }
int messageSent = 0;
  private void logNoseLandmark(FaceMeshResult result) {
    //This method is used to log the total values.
    if (result == null || result.multiFaceLandmarks().isEmpty()) {
      return;
    }
    textView2 = findViewById(R.id.textView2);
    NormalizedLandmark noseLandmark = result.multiFaceLandmarks().get(0).getLandmarkList().get(1);
    double Yawning = detectYawning(result);
    double Eyes1 = detectEyes1(result);
    double Eyes2 = detectEyes2(result);
    String s1 = String.valueOf(Eyes1);
    String s2 = String.valueOf(Yawning);
    if(Yawning > 6.0 && (Eyes1<0.7 && Eyes2<0.7) )
    {
      playSound();
    }
    if((Eyescount > 10 && Eyescount < 20) || (Eyescount>50 && Eyescount<60) || (Eyescount>100 &&
            Eyescount<110))
    {
      playSound();
      if(Eyescount > 100)
      {
        sendSOS();
        messageSent = 1;
        Eyescount = 0;
      }
      Log.i("Eyescount", String.valueOf(Eyescount));
    }
    else
    {
      pauseSound();
    }
    if((HeadCount >50 && HeadCount <60) || (HeadCount > 100 && HeadCount<110) || (HeadCount > 150 && HeadCount<170))
    {
      playSound();
      if(HeadCount > 50)
      {
        sendSOS();
        messageSent = 1;
        HeadCount = 0;
      }
      Log.i("Headcount",String.valueOf(HeadCount));
    }
    else {
      pauseSound();
    }
    textView2.setText("EAR: "+s1+ "MAR: " +s2);
    overallAlgorithm(result);


    Log.i(
          TAG,
          String.format(
              "MediaPipe Face Mesh nose normalized coordinates (value range: [0, 1]): x=%f, y=%f",
              noseLandmark.getX(), noseLandmark.getY()));
  }
}