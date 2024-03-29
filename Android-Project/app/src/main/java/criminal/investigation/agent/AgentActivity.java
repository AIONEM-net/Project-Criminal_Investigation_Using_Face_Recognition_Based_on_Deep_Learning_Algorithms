package criminal.investigation.agent;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.util.Date;

import criminal.investigation.LoginActivity;
import criminal.investigation.cctv.R;

public class AgentActivity extends AppCompatActivity {

    String myDistrict = "";

    public static FirebaseUser firebaseUser;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agent);

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();

        if (firebaseUser == null) {
            Intent intent = new Intent(AgentActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setSupportActionBar(findViewById(R.id.toolbar));

        TextView txtLocation = (TextView) findViewById(R.id.txtLocation);
        TextView txtName = (TextView) findViewById(R.id.txtName);
        TextView txtIdentity = (TextView) findViewById(R.id.txtIdentity);
        TextView txtGender = (TextView) findViewById(R.id.txtGender);
        TextView txtTime = (TextView) findViewById(R.id.txtTime);
        TextView txtAccuracy = (TextView) findViewById(R.id.txtAccuracy);
        ImageView imgFace = (ImageView) findViewById(R.id.imgFace);


        findViewById(R.id.btnReports).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(AgentActivity.this, ReportsActivity.class);
                startActivity(intent);

            }
        });


        ValueEventListener valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot data) {

                String district = data.hasChild("district") ? String.valueOf(data.child("district").getValue()) : "";
                String location = data.hasChild("location") ? String.valueOf(data.child("location").getValue()) : "";
                String name = data.hasChild("name") ? String.valueOf(data.child("name").getValue()) : "";
                String identity = data.hasChild("identity") ? String.valueOf(data.child("identity").getValue()) : "";
                String gender = data.hasChild("gender") ? String.valueOf(data.child("gender").getValue()) : "";
                String photo = data.hasChild("photo") ? String.valueOf(data.child("photo").getValue()) : "";
                String accuracy = data.hasChild("accuracy") ? String.valueOf(data.child("accuracy").getValue()) : "";
                long time = data.hasChild("time") ? data.child("time").getValue(Long.class) : 0;
                boolean isRecognized = data.hasChild("isRecognized") ? data.child("isRecognized").getValue(Boolean.class) : false;

                long timeInterval = System.currentTimeMillis() - time;


                if(TextUtils.isEmpty(district) || district.equalsIgnoreCase(myDistrict)) {

                    if (isRecognized) {

                        FirebaseDatabase.getInstance().getReference("Criminals").child(identity).addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot data) {

                                boolean isTracking = data.hasChild("isTracking") ? data.child("isTracking").getValue(Boolean.class) : false;
                                boolean isCaught = data.hasChild("isCaught") ? data.child("isCaught").getValue(Boolean.class) : false;

                                if (isTracking) {

                                    txtLocation.setText((!TextUtils.isEmpty(district) ? district : "All") +" : "+ location);
                                    txtName.setText(name);
                                    txtIdentity.setText(identity);
                                    txtGender.setText(gender);
                                    txtTime.setText(new Date(time).toString());
                                    txtAccuracy.setText(accuracy + " %");

                                    new DownloadImageTask(imgFace).execute(photo);

                                    txtAccuracy.setText(accuracy + " %" + " | " + (isCaught ? "is Caught" : "is Detected"));

                                    if (timeInterval < 1 * 60 * 1000) {

                                        if (isCaught) {

                                            sendNotification(AgentActivity.this,
                                                    (identity + "Caught").hashCode(),
                                                    name + " is Caught",
                                                    identity + " - " + gender + " - " + accuracy + " %"
                                            );

                                        } else {

                                            sendNotification(AgentActivity.this,
                                                    (identity + "Detected").hashCode(),
                                                    name + "'s Face Detected" + " at " + district +" : "+ location,
                                                    identity + " - " + gender + " - " + accuracy + " %"
                                            );

                                        }

                                    }

                                }else {

                                    txtLocation.setText("");
                                    txtName.setText("");
                                    txtIdentity.setText("");
                                    txtGender.setText("");
                                    txtTime.setText("");
                                    txtAccuracy.setText("");

                                    new DownloadImageTask(imgFace).execute("");

                                }

                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {

                            }
                        });

                    }

                }else {

                    txtLocation.setText("You don't have Location Access");
                    txtName.setText("");
                    txtIdentity.setText("");
                    txtGender.setText("");
                    txtTime.setText("");
                    txtAccuracy.setText("");
                    imgFace.setImageResource(R.drawable.logo_rib);

                    txtLocation.setBackgroundColor(Color.parseColor("#C1940C"));

                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }

        };

        txtLocation.setText("----");
        txtLocation.setBackgroundColor(Color.parseColor("#FF0000"));

        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("CCTV-Camera");

        FirebaseDatabase.getInstance().getReference("Agents").child(firebaseUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot data) {

                myDistrict = data.hasChild("district") ? data.child("district").getValue(String.class) : "";
                boolean isActive = data.hasChild("isActive") ? data.child("isActive").getValue(Boolean.class) : false;

                if(isActive) {

                    databaseReference.addValueEventListener(valueEventListener);

                    txtLocation.setBackgroundColor(Color.parseColor("#009688"));


                }else {

                    databaseReference.removeEventListener(valueEventListener);

                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(AgentActivity.this);

                    notificationManager.cancelAll();

                    txtLocation.setText("You are not Approved");
                    txtName.setText("");
                    txtIdentity.setText("");
                    txtGender.setText("");
                    txtTime.setText("");
                    txtAccuracy.setText("");
                    imgFace.setImageResource(R.drawable.logo_rib);

                    txtLocation.setBackgroundColor(Color.parseColor("#FF0000"));

                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }


    private static final String CHANNEL_ID = "CCTV Camera - Criminal Investigation";
    private static final String CHANNEL_NAME = "CCTV Camera - Criminal Investigation";
    private static final String CHANNEL_DESC = "CCTV Camera - Criminal Investigation";

    public static Ringtone ringtone;

    public static void sendNotification(Context context, int id, String title, String message) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setDescription(CHANNEL_DESC);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(notificationChannel);
        }

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        if (ringtone == null) {
            Uri rawPathUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.danger);
            ringtone = RingtoneManager.getRingtone(context, rawPathUri);
        }

        ringtone.stop();

        if(true || !ringtone.isPlaying()) {
            ringtone.play();
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.logo_rib)
                .setContentTitle(title)
                .setContentText(message)
                 .setSound(soundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        notificationManager.notify(id, builder.build());
    }

    private static class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        @SuppressLint("StaticFieldLeak")
        ImageView imageView;

        public DownloadImageTask(ImageView bmImage) {
            this.imageView = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String url = urls[0];
            Bitmap bitmap = null;
            if(!TextUtils.isEmpty(url)) {
                try {
                    InputStream inputStream = new java.net.URL(url).openStream();
                    bitmap = BitmapFactory.decodeStream(inputStream);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return bitmap;
        }

        protected void onPostExecute(Bitmap result) {
            if(result != null) {
                imageView.setImageBitmap(result);
            }else {
                imageView.setImageResource(R.drawable.logo_face);
            }
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar_agent, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_reports) {
            Intent intent = new Intent(this, ReportsActivity.class);
            startActivity(intent);
            return false;
        }else if (itemId == R.id.action_logout) {
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

}