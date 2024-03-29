package criminal.investigation.cctv;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.util.Pair;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import criminal.investigation.LoginActivity;
import criminal.investigation.agent.AgentActivity;


public class CCTVCameraActivity extends AppCompatActivity {

    FaceDetector detector;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    PreviewView previewView;
    ImageView face_preview;
    Interpreter tfLite;
    TextView reco_name,preview_info;
    Toolbar recognize; //Button recognize
    Button camera_switch, actions;
    ImageButton add_face;
    CameraSelector cameraSelector;
    boolean start = true,flipX=false;
    boolean isRecognizing = true;
    Context context= CCTVCameraActivity.this;
    int cam_face=CameraSelector.LENS_FACING_BACK; //Default Back Camera

    int[] intValues;
    int inputSize=112;  //Input size for model
    boolean isModelQuantized=false;
    float[][] embeedings;
    float IMAGE_MEAN = 128.0f;
    float IMAGE_STD = 128.0f;
    int OUTPUT_SIZE=192; //Output size of model
    private static int SELECT_PICTURE = 1;
    ProcessCameraProvider cameraProvider;
    private static final int MY_CAMERA_REQUEST_CODE = 100;

    //model name
    String modelFile="mobile_face_net.tflite";

    //saved Faces
    private HashMap<String, Recognition> registered = new HashMap<>();

    private FirebaseDatabase firebaseDatabase;

    String cctvDistrict = "";
    String cctvLocation = "";


    public static FirebaseUser firebaseUser;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Load saved faces from memory when app starts
        registered = readFromSP();
        setContentView(R.layout.activity_cctv_camera);

        firebaseDatabase = FirebaseDatabase.getInstance("https://criminal-investigation-face-default-rtdb.firebaseio.com");

        setSupportActionBar(findViewById(R.id.button3));

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();

        if (firebaseUser == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        face_preview =findViewById(R.id.imageView);
        reco_name =findViewById(R.id.textView);
        preview_info =findViewById(R.id.textView2);
        add_face=findViewById(R.id.imageButton);
        add_face.setVisibility(View.GONE);

        face_preview.setVisibility(View.GONE);
        recognize=findViewById(R.id.button3);
        camera_switch=findViewById(R.id.button5);
        actions=findViewById(R.id.button2);
        preview_info.setText("\nRecognized Face:");
        preview_info.setText("");
        //Camera Permission
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }
        //On-screen Action Button
        actions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Select Action:");

                // add a checkbox list
                String [] names= {"View Face Recognition List","Update Face Recognition List","Save Face Recognitions","Load Face Recognitions","Clear All Face Recognitions","Train Face from Photo", "Train Face from Camera", "Open Face Detector WebCAM"};

                builder.setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        switch (which)
                        {
                            case 0:
                                displaynameListview();
                                break;
                            case 1:
                                updatenameListview();
                                break;
                            case 2:
                                insertToSP(registered,false);
                                break;
                            case 3:
                                registered.putAll(readFromSP());
                                break;
                            case 4:
                                clearnameList();
                                break;
                            case 5:
                                face_preview.setImageBitmap(null);
                                findViewById(R.id.coordinatorLayout).setBackgroundColor(getResources().getColor(R.color.black));
                                loadphoto();
                                break;
                            case 6:

                                isRecognizing = false;
                                // recognize.setText("SAVE Face To Database");
                                recognize.setTitle("SAVE Face To Database");
                                add_face.setVisibility(View.VISIBLE);
                                reco_name.setVisibility(View.GONE);
                                face_preview.setVisibility(View.VISIBLE);
                                face_preview.setImageBitmap(null);
                                findViewById(R.id.coordinatorLayout).setBackgroundColor(getResources().getColor(R.color.black));
                                preview_info.setText("1.Bring Face in view of Camera.\n\n2.Detected Face photo will appear here.\n\n3.Click Add button to save face.");

                                break;
                            case 7:

                                isRecognizing = true;
                                start = true;
                                // recognize.setText("FACE RECOGNITION | CCTV CAMERA");
                                recognize.setTitle("FACE RECOGNITION | CCTV CAMERA");
                                add_face.setVisibility(View.GONE);
                                reco_name.setVisibility(View.VISIBLE);
                                face_preview.setVisibility(View.GONE);
                                preview_info.setText("\nRecognized Face:");
                                preview_info.setText("");

                                break;
                        }

                    }
                });


                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.setNegativeButton("Cancel", null);

                // create and show the alert dialog
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        //On-screen switch to toggle between Cameras.
        camera_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cam_face==CameraSelector.LENS_FACING_BACK) {
                    cam_face = CameraSelector.LENS_FACING_FRONT;
                    flipX=true;
                }
                else {
                    cam_face = CameraSelector.LENS_FACING_BACK;
                    flipX=false;
                }
                cameraProvider.unbindAll();
                cameraBind();
            }
        });

        add_face.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                addFace();
            }
        }));


        recognize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                if (!recognize.getText().toString().equalsIgnoreCase("FACE RECOGNITION | CCTV CAMERA")) {
//                    addFace();
//                }
                if (!recognize.getTitle().toString().equalsIgnoreCase("FACE RECOGNITION | CCTV CAMERA")) {
                    addFace();
                }
            }
        });

        //Load model
        try {
            tfLite=new Interpreter(loadModelFile(CCTVCameraActivity.this,modelFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Initialize Face Detector
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        detector = FaceDetection.getClient(highAccuracyOpts);

        cameraBind();

        FirebaseDatabase.getInstance().getReference("CCTV-Camera").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot data) {

                cctvDistrict = data.hasChild("district") ? String.valueOf(data.child("district").getValue()) : "";
                cctvLocation = data.hasChild("location") ? String.valueOf(data.child("location").getValue()) : "";

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        FirebaseDatabase.getInstance().getReference("Admins").child(firebaseUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot data) {

                boolean isActive = data.hasChild("isActive") ? data.child("isActive").getValue(Boolean.class) : false;

                if(isActive) {

                    findViewById(R.id.rLayoutAccess).setVisibility(View.GONE);

                }else {

                    Toast.makeText(getApplicationContext(), "Your Admin Account is not Activated", Toast.LENGTH_LONG).show();

                    findViewById(R.id.rLayoutAccess).setVisibility(View.VISIBLE);

                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }

    AlertDialog.Builder alertDialog;
    EditText inputName = null;
    EditText inputID = null;
    EditText inputGender = null;
    String name = "";
    String identity = "";
    String gender = "";

    @Override
    public void onBackPressed() {
        super.onBackPressed();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        insertToSP(registered,false);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar_cctv, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_logout) {
            logout();
            return false;
        }
        return super.onOptionsItemSelected(item);
    }

    public void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }


    int faceNo = 0;
    String lastID = "";

    private void addFace() {

        start = false;

        faceNo = faceNo > 5 ? 0 : faceNo;

        alertDialog = new AlertDialog.Builder(context);
        alertDialog.setTitle("Train FACE to DataBase (" + (faceNo + 1) + ")");

        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        linearLayout.setLayoutParams(lp);

        inputName = new EditText(CCTVCameraActivity.this);
        LinearLayout.LayoutParams lpInputName = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        inputName.setLayoutParams(lpInputName);

        inputID = new EditText(CCTVCameraActivity.this);
        LinearLayout.LayoutParams lpInputPhone = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        inputID.setLayoutParams(lpInputPhone);

        inputGender = new EditText(CCTVCameraActivity.this);
        LinearLayout.LayoutParams lpInputGender = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        inputGender.setLayoutParams(lpInputGender);

        CheckBox checkboxGenderMale = new CheckBox(CCTVCameraActivity.this);
        LinearLayout.LayoutParams lpInputGenderMale = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        checkboxGenderMale.setLayoutParams(lpInputGenderMale);

        CheckBox checkboxGenderFemale = new CheckBox(CCTVCameraActivity.this);
        LinearLayout.LayoutParams lpInputGenderFemale = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        checkboxGenderFemale.setLayoutParams(lpInputGenderFemale);

        linearLayout.addView(inputName);
        linearLayout.addView(inputID);
        linearLayout.addView(inputGender);
        linearLayout.addView(checkboxGenderMale);
        linearLayout.addView(checkboxGenderFemale);

        alertDialog.setView(linearLayout);


        inputName.setHint("Enter Person Name");
        inputName.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        inputName.setText(name);

        inputID.setHint("Enter Person ID Number");
        inputID.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputID.setText(identity);

        inputGender.setHint("Select Person Gender");
        inputGender.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        inputGender.setText(gender);
        inputGender.setEnabled(false);

        checkboxGenderMale.setText("MALE");
        checkboxGenderMale.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    checkboxGenderFemale.setChecked(false);
                }
            }
        });

        checkboxGenderFemale.setText("FEMALE");
        checkboxGenderFemale.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    checkboxGenderMale.setChecked(false);
                }
            }
        });

        if ("male".equalsIgnoreCase(gender)) {
            checkboxGenderMale.setChecked(true);
        } else if ("female".equalsIgnoreCase(gender)) {
            checkboxGenderFemale.setChecked(true);
        }


        alertDialog.setPositiveButton("SAVE", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                name = inputName.getText().toString();
                identity = inputID.getText().toString();

                if (checkboxGenderMale.isChecked()) {
                    inputGender.setText("MALE");
                } else if (checkboxGenderFemale.isChecked()) {
                    inputGender.setText("FEMALE");
                }
                gender = inputGender.getText().toString();

                if (!name.contains(" ")) {
                    Toast.makeText(CCTVCameraActivity.this, "Enter a valid Name", Toast.LENGTH_LONG).show();
                    inputName.setError("Invalid Name!");
                    // alertDialog.show();
                    return;
                }
                inputName.setError(null);

                if (identity.length() != 16) {
                    Toast.makeText(CCTVCameraActivity.this, "Enter a valid ID Number", Toast.LENGTH_LONG).show();
                    inputID.setError("Invalid ID Number!");
                    // alertDialog.show();
                    return;
                }
                if (!identity.startsWith("119") && !identity.startsWith("120")) {
                    Toast.makeText(CCTVCameraActivity.this, "Enter a valid ID Number", Toast.LENGTH_LONG).show();
                    inputID.setError("Invalid ID Number!");
                    // alertDialog.show();
                    return;
                }
                inputID.setError(null);

                if (gender.isEmpty()) {
                    Toast.makeText(CCTVCameraActivity.this, "Select Gender", Toast.LENGTH_LONG).show();
                    inputGender.setError("Gender required!");
                    // alertDialog.show();
                    return;
                }
                inputGender.setError(null);

                Recognition result = new Recognition(identity, name, -1f, gender);
                result.setExtra(embeedings);

                registered.put(name, result);
                start = true;

                if (identity.equals(lastID)) {
                    faceNo++;
                } else {
                    faceNo = 0;
                }

                lastID = identity;

                long time = Calendar.getInstance().getTime().getTime();

                HashMap<String, Object> mapData = new HashMap<>();
                mapData.put("name", name);
                mapData.put("identity", identity);
                mapData.put("gender", gender);
                mapData.put("time", time);

                DatabaseReference databaseReference = firebaseDatabase.getReference("Criminals").child(identity);

                for (String key : mapData.keySet()) {
                    Object value = mapData.get(key);
                    databaseReference.child(key).setValue(value);
                }

                Bitmap bitmap = ((BitmapDrawable) face_preview.getDrawable()).getBitmap();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                byte[] data = baos.toByteArray();

                StorageReference storageReference = FirebaseStorage.getInstance().getReference().child(("face-" + name + "-" + identity).replace(" ", "") + ".jpg");

                UploadTask uploadTask = storageReference.putBytes(data);
                Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                    @Override
                    public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                        return storageReference.getDownloadUrl();
                    }
                }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                    @Override
                    public void onComplete(@NonNull Task<Uri> task) {
                        String photo = "";
                        if (task.isSuccessful()) {
                            Uri downloadUri = task.getResult();
                            photo = String.valueOf(downloadUri);
                        } else {
                            photo = "";
                        }
                        if (faceNo > 5) {
                            faceNo = 0;

                            name = "";
                            identity = "";
                            gender = "";
                        }
                        databaseReference.child("photo" + (faceNo > 0 ? faceNo : "")).setValue(photo);
                    }
                });

                dialog.dismiss();

                Toast.makeText(CCTVCameraActivity.this, "Face Training saved to Database", Toast.LENGTH_LONG).show();
            }
        });
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                start = true;
                dialog.cancel();
            }
        });

        alertDialog.show();

    }

    private  void clearnameList()
    {
        AlertDialog.Builder builder =new AlertDialog.Builder(context);
        builder.setTitle("Do you want to delete all Recognitions?");
        builder.setPositiveButton("Delete All", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                registered.clear();
                Toast.makeText(context, "Recognitions Cleared", Toast.LENGTH_SHORT).show();
            }
        });
        insertToSP(registered,true);
        builder.setNegativeButton("Cancel",null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void updatenameListview()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if(registered.isEmpty()) {
            builder.setTitle("No Faces Added!!");
            builder.setPositiveButton("OK",null);
        }
        else{
            builder.setTitle("Select Recognition to delete:");

        // add a checkbox list
        String[] names= new String[registered.size()];
        boolean[] checkedItems = new boolean[registered.size()];
         int i=0;
                for (Map.Entry<String, Recognition> entry : registered.entrySet())
                {
                    //System.out.println("NAME"+entry.getKey());
                    names[i]=entry.getKey();
                    checkedItems[i]=false;
                    i=i+1;

                }

                builder.setMultiChoiceItems(names, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        // user checked or unchecked a box
                        //Toast.makeText(MainActivity.this, names[which], Toast.LENGTH_SHORT).show();
                       checkedItems[which]=isChecked;

                    }
                });


        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                       // System.out.println("status:"+ Arrays.toString(checkedItems));
                        for(int i=0;i<checkedItems.length;i++)
                        {
                            //System.out.println("status:"+checkedItems[i]);
                            if(checkedItems[i])
                            {
//                                Toast.makeText(MainActivity.this, names[i], Toast.LENGTH_SHORT).show();
                                registered.remove(names[i]);
                            }

                        }
                Toast.makeText(context, "Recognitions Updated", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);

        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    }
    private void displaynameListview()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
       // System.out.println("Registered"+registered);
        if(registered.isEmpty())
            builder.setTitle("No Faces Added!!");
        else
            builder.setTitle("Recognitions:");

        // add a checkbox list
        String[] names= new String[registered.size()];
        boolean[] checkedItems = new boolean[registered.size()];
        int i=0;
        for (Map.Entry<String, Recognition> entry : registered.entrySet()) {
            names[i]=entry.getKey() +" ("+ entry.getValue().getId() +")";
            checkedItems[i]=false;
            i=i+1;
        }
        builder.setItems(names,null);



        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

            // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    //Bind camera and preview view
    private void cameraBind()
    {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        previewView=findViewById(R.id.previewView);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this in Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cam_face)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //Latest frame is shown
                        .build();

        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {

                InputImage image = null;


                @SuppressLint({"UnsafeExperimentalUsageError", "UnsafeOptInUsageError"})
                // Camera Feed-->Analyzer-->ImageProxy-->mediaImage-->InputImage(needed for ML kit face detection)

                Image mediaImage = imageProxy.getImage();

                if (mediaImage != null) {
                    image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                    System.out.println("Rotation "+imageProxy.getImageInfo().getRotationDegrees());
                }

                System.out.println("ANALYSIS");

                //Process acquired image to detect faces
                Task<List<Face>> result =
                        detector.process(image)
                                .addOnSuccessListener(
                                        new OnSuccessListener<List<Face>>() {
                                            @Override
                                            public void onSuccess(List<Face> faces) {

                                                if(faces.size()!=0) {
                                                    Face face = faces.get(0); //Get first face from detected faces
                                                    System.out.println(face);

                                                    //mediaImage to Bitmap
                                                    Bitmap frame_bmp = toBitmap(mediaImage);

                                                    int rot = imageProxy.getImageInfo().getRotationDegrees();

                                                    //Adjust orientation of Face
                                                    Bitmap frame_bmp1 = rotateBitmap(frame_bmp, rot, false, false);



                                                    //Get bounding box of face
                                                    RectF boundingBox = new RectF(face.getBoundingBox());

                                                    //Crop out bounding box from whole Bitmap(image)
                                                    Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);

                                                    if(flipX)
                                                        cropped_face = rotateBitmap(cropped_face, 0, flipX, false);
                                                    //Scale the acquired Face to 112*112 which is required input for model
                                                    Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);

                                                    if(start)
                                                        recognizeImage(scaled); //Send scaled bitmap to create face embeddings.
                                                    System.out.println(boundingBox);
                                                    try {
                                                        Thread.sleep(10);  //Camera preview refreshed every 10 millisec(adjust as required)
                                                    } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                else
                                                {
                                                    if(registered.isEmpty()) {
                                                        reco_name.setText("Add Face");
                                                        findViewById(R.id.coordinatorLayout).setBackgroundColor(getResources().getColor(R.color.black));
                                                    } else {
                                                        reco_name.setText("No Face Detected!");
                                                        findViewById(R.id.coordinatorLayout).setBackgroundColor(getResources().getColor(R.color.no_face));
                                                    }
                                                }

                                            }
                                        })
                                .addOnFailureListener(
                                        new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                // Task failed with an exception
                                                // ...
                                            }
                                        })
                                .addOnCompleteListener(new OnCompleteListener<List<Face>>() {
                            @Override
                            public void onComplete(@NonNull Task<List<Face>> task) {

                                imageProxy.close(); //v.important to acquire next frame for analysis
                            }
                        });


            }
        });


        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);


    }

    public void recognizeImage(final Bitmap bitmap) {

        // set Face to Preview
        face_preview.setImageBitmap(bitmap);

        //Create ByteBuffer to store normalized image

        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);

        imgData.order(ByteOrder.nativeOrder());

        intValues = new int[inputSize * inputSize];

        //get pixel values from Bitmap to normalize
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();

        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);

                }
            }
        }
        //imgData is input to our model
        Object[] inputArray = {imgData};

        Map<Integer, Object> outputMap = new HashMap<>();


        embeedings = new float[1][OUTPUT_SIZE]; //output of model will be stored in this variable

        outputMap.put(0, embeedings);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap); //Run model



        float distance = Float.MAX_VALUE;

        //Compare new face with saved Faces.
        if (registered.size() > 0) {

            final Pair<String, Float> nearest = findNearest(embeedings[0]);//Find closest matching face

            if (nearest != null) {

                final String name = nearest.first;
                distance = nearest.second;
                Recognition recognition = registered.get(name);

                if (distance < 1.000f) { //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
                    reco_name.setText(name);
                    onFaceRecognized(name, distance, recognition, bitmap);
                } else {
                    reco_name.setText("Unknown");
                    onFaceRecognized("Unknown", distance, null, null);
                }

                System.out.println("nearest: " + name + " - distance: " + distance);
            }
        }

    }

    //Compare Faces by distance between face embeddings
    private Pair<String, Float> findNearest(float[] emb) {

        Pair<String, Float> ret = null;
        for (Map.Entry<String, Recognition> entry : registered.entrySet()) {

            final String name = entry.getKey();
           final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff*diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                ret = new Pair<>(name, distance);
            }
        }

        return ret;

    }
    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }
    private static Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(),
                (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas cavas = new Canvas(resultBitmap);

        // draw background
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        cavas.drawRect(//from  w w  w. ja v  a  2s. c  om
                new RectF(0, 0, cropRectF.width(), cropRectF.height()),
                paint);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);

        cavas.drawBitmap(source, matrix, paint);

        if (source != null && !source.isRecycled()) {
            source.recycle();
        }

        return resultBitmap;
    }

    private static Bitmap rotateBitmap(
            Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees);

        // Mirror the image along the X or Y axis.
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    //IMPORTANT. If conversion not done ,the toBitmap conversion does not work on some devices.
    private static byte[] YUV_420_888toNV21(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width*height;
        int uvSize = width*height/4;

        byte[] nv21 = new byte[ySize + uvSize*2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();
        assert(image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        }
        else {
            long yBufferPos = -rowStride; // not an actual position
            for (; pos<ySize; pos+=width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert(rowStride == image.getPlanes()[1].getRowStride());
        assert(pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte)~savePixel);
                if (uBuffer.get(0) == (byte)~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());

                    return nv21; // shortcut
                }
            }
            catch (ReadOnlyBufferException ex) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel);
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (int row=0; row<height/2; row++) {
            for (int col=0; col<width/2; col++) {
                int vuPos = col*pixelStride + row*rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    private Bitmap toBitmap(Image image) {

        byte[] nv21=YUV_420_888toNV21(image);


        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        //System.out.println("bytes"+ Arrays.toString(imageBytes));

        //System.out.println("FORMAT"+image.getFormat());

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    //Save Faces to Shared Preferences.Conversion of Recognition objects to json string
    private void insertToSP(HashMap<String, Recognition> jsonMap, boolean clear) {
        if(clear)
            jsonMap.clear();
        else
            jsonMap.putAll(readFromSP());
        String jsonString = new Gson().toJson(jsonMap);
//        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : jsonMap.entrySet())
//        {
//            System.out.println("Entry Input "+entry.getKey()+" "+  entry.getValue().getExtra());
//        }
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("map", jsonString);
        //System.out.println("Input josn"+jsonString.toString());
        editor.apply();
        Toast.makeText(context, "Recognitions Saved", Toast.LENGTH_SHORT).show();
    }

    //Load Faces from Shared Preferences.Json String to Recognition object
    private HashMap<String, Recognition> readFromSP(){
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        String defValue = new Gson().toJson(new HashMap<String, Recognition>());
        String json=sharedPreferences.getString("map",defValue);
       // System.out.println("Output json"+json.toString());
        TypeToken<HashMap<String, Recognition>> token = new TypeToken<HashMap<String, Recognition>>() {};
        HashMap<String, Recognition> retrievedMap=new Gson().fromJson(json,token.getType());
       // System.out.println("Output map"+retrievedMap.toString());

        //During type conversion and save/load procedure,format changes(eg float converted to double).
        //So embeddings need to be extracted from it in required format(eg.double to float).
        for (Map.Entry<String, Recognition> entry : retrievedMap.entrySet())
        {
            float[][] output=new float[1][OUTPUT_SIZE];
            ArrayList arrayList= (ArrayList) entry.getValue().getExtra();
            arrayList = (ArrayList) arrayList.get(0);
            for (int counter = 0; counter < arrayList.size(); counter++) {
                output[0][counter]= ((Double) arrayList.get(counter)).floatValue();
            }
            entry.getValue().setExtra(output);

            //System.out.println("Entry output "+entry.getKey()+" "+entry.getValue().getExtra() );
        }
//        System.out.println("OUTPUT"+ Arrays.deepToString(outut));
        Toast.makeText(context, "Recognitions Loaded", Toast.LENGTH_SHORT).show();
        return retrievedMap;
    }

    //Load Photo from phone storage
    private void loadphoto()
    {
        start = false;
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);
    }

    //Similar Analyzing Procedure
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                try {
                    InputImage impphoto=InputImage.fromBitmap(getBitmapFromUri(selectedImageUri),0);
                    detector.process(impphoto).addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                        @Override
                        public void onSuccess(List<Face> faces) {

                            if(faces.size()!=0) {
                                isRecognizing = false;
                                // recognize.setText("RECOGNIZED FACE");
                                recognize.setTitle("RECOGNIZED FACE");
                                add_face.setVisibility(View.VISIBLE);
                                reco_name.setVisibility(View.GONE);
                                face_preview.setVisibility(View.VISIBLE);
                                preview_info.setText("1.Bring Face in view of Camera.\n\n2.Detected Face photo will appear here.\n\n3.Click Add button to save face.");
                                Face face = faces.get(0);
                                System.out.println(face);

                                //write code to recreate bitmap from source
                                //Write code to show bitmap to canvas

                                Bitmap frame_bmp= null;
                                try {
                                    frame_bmp = getBitmapFromUri(selectedImageUri);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                Bitmap frame_bmp1 = rotateBitmap(frame_bmp, 0, flipX, false);

                                //face_preview.setImageBitmap(frame_bmp1);


                                RectF boundingBox = new RectF(face.getBoundingBox());


                                Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);

                                Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);
                                // face_preview.setImageBitmap(scaled);

                                    recognizeImage(scaled);
                                    addFace();
                                System.out.println(boundingBox);
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            start = true;
                            Toast.makeText(context, "Failed to add", Toast.LENGTH_SHORT).show();
                        }
                    });
                    face_preview.setImageBitmap(getBitmapFromUri(selectedImageUri));
                } catch (IOException e) {
                    e.printStackTrace();
                }


            }
        }
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }


    private void onFaceRecognized(String name, float distance, Recognition recognition, Bitmap bitmap) {

        if(isRecognizing) {

            double accuracy;

            if(distance > 1) {
                accuracy = ((distance - 1)*100.0) - 20;
                if(accuracy < 0) accuracy = 0;
            }else {
                accuracy = (100 - (1 - distance)*100.0) + 10;
                if(accuracy > 100) accuracy = 100;
            }

            long time = Calendar.getInstance().getTime().getTime();

            HashMap<String, Object> mapData = new HashMap<>();
            mapData.put("identity", "");
            mapData.put("name", "");
            mapData.put("gender", "");
            mapData.put("distance", "");
            mapData.put("accuracy", "");
            mapData.put("time", time);
            mapData.put("isRecognized", false);

            DatabaseReference databaseReferenceCCTV = firebaseDatabase.getReference("CCTV-Camera");

            if (recognition != null) {

                findViewById(R.id.coordinatorLayout).setBackgroundColor(getResources().getColor(R.color.found_yes));

                mapData.put("identity", recognition.getId());
                mapData.put("name", recognition.getName());
                mapData.put("gender", recognition.getGender());
                mapData.put("distance", distance);
                mapData.put("accuracy", (int) accuracy);
                mapData.put("photo", "");
                mapData.put("time", time);
                mapData.put("isRecognized", true);
                mapData.put("district", cctvDistrict);
                mapData.put("location", cctvLocation);

                DatabaseReference databaseReferenceDetections = firebaseDatabase.getReference("Detections").push();

                databaseReferenceDetections.setValue(mapData);

                firebaseDatabase.getReference("Criminals").child(recognition.getId()).child("isDetected").setValue(true);


                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                byte[] data = baos.toByteArray();

                StorageReference storageReference = FirebaseStorage.getInstance().getReference().child("face-"+(recognition.getName()+"-"+recognition.getId()).replace(" ", "")+"-1.jpg");

                UploadTask uploadTask = storageReference.putBytes(data);
                Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                    @Override
                    public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                        return storageReference.getDownloadUrl();
                    }
                }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                    @Override
                    public void onComplete(@NonNull Task<Uri> task) {
                        String photo = "";
                        if (task.isSuccessful()) {
                            Uri downloadUri = task.getResult();
                            photo = String.valueOf(downloadUri);
                        } else {
                            photo = "";
                        }
                        databaseReferenceCCTV.child("photo").setValue(photo);
                        databaseReferenceDetections.child("photo").setValue(photo);
                    }
                });

            }else {
                findViewById(R.id.coordinatorLayout).setBackgroundColor(getResources().getColor(R.color.found_no));
            }

            for(String key : mapData.keySet()) {
                databaseReferenceCCTV.child(key).setValue(mapData.get(key));
            }

        }else {
            findViewById(R.id.coordinatorLayout).setBackgroundColor(getResources().getColor(R.color.black));
        }

    }

}
