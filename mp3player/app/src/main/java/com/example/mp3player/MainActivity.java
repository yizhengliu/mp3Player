package com.example.mp3player;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.database.Cursor;
import android.provider.MediaStore;
import android.widget.AdapterView;
import android.widget.SeekBar;
import android.widget.SimpleCursorAdapter;
import android.os.Bundle;
import android.widget.TextView;

//activity for user to control the application
public class MainActivity extends AppCompatActivity {
    //keep binder interface reference here for further interaction with the service
    private mp3PlayService.MyBinder binderInterface;
    //keep references of views here for any updates
    private SeekBar progress;
    private ListView lv;
    private TextView currentTime, totalTime;
    //references the song data
    private String[] uris;
    private int numOfSong, currentPos = -1;

    //when activity is created, fill the list view with songs from the storage of the mobile
    //initialize member variables, and bind the service just in case callbacks would work and
    //the action made from UI is updated in the service
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        lv = findViewById(R.id.listView);
        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null,
                MediaStore.Audio.Media.IS_MUSIC + "!= 0",
                null,
                null);

        lv.setAdapter(new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1,
                cursor,
                new String[]{MediaStore.Audio.Media.DATA},
                new int[]{android.R.id.text1}));
        numOfSong = lv.getAdapter().getCount();
        uris = new String[numOfSong];
        for(int i = 0;i < numOfSong; i++){
            @SuppressLint("Range") String uri = ((Cursor)lv.getItemAtPosition(i)).getString(((Cursor)lv.getItemAtPosition(i)).getColumnIndex(MediaStore.Audio.Media.DATA));
            uris[i] = uri;
        }
        Log.d("comp3018", "there are " + lv.getAdapter().getCount() + " songs");
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> myAdapter,
                                    View myView,
                                    int myItemInt,
                                    long mylng) {
                Log.d("comp3018", "current pos: " + myItemInt);
                currentPos = myItemInt;
                startPlayingMusic();
            }
        });
        //bind the service
        Intent intent = new Intent(MainActivity.this, mp3PlayService.class);
        this.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        progress = findViewById(R.id.currentProgress);
        currentTime = findViewById(R.id.currentTime);
        totalTime = findViewById(R.id.totalTime);
        //give the progress a listener if user want to change where the track will be played
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                currentTime.setText(getString(R.string.time, (seekBar.getProgress() / 60000), (seekBar.getProgress() / 1000) % 60));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(binderInterface != null)
                    binderInterface.seekbarChanged(seekBar.getProgress());
            }
        });
    }

    //when service is connected with the activity, update the duration of the song if service was still there
    //and inform the service the activity is there for any call back methods
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d("g53mdp", "MainActivity onServiceConnected");
            binderInterface = (mp3PlayService.MyBinder) service;
            binderInterface.registerCallback(callback);
            binderInterface.gainingConnection();
            new Thread(() -> runOnUiThread(
                    () -> {
                        totalTime.setText(getString(R.string.time, (binderInterface.totalTime() / 60000), (binderInterface.totalTime() / 1000) % 60));
                        progress.setMax(binderInterface.totalTime());
                        if(!binderInterface.isRepeating())
                            ((Button)findViewById(R.id.loop)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.loop_list,0,0,0);
                        else
                            ((Button)findViewById(R.id.loop)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.repeat_one,0,0,0);
                        progress.setProgress(binderInterface.getCurrentProgress());
                    }
            )).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("g53mdp", "MainActivity onServiceDisconnected");
            binderInterface.unregisterCallback(callback);
            binderInterface = null;
        }
        //call back methods for song playing(update the seekbar of the current progress),
        //set the duration time of a new song, inform the activity that a playing song is stopped,
        //and which song is current playing
        final ICallback callback = new ICallback() {

            @Override
            public void playingEvent(int currentProgress) {
                runOnUiThread(
                        () -> {
                            currentTime.setText(getString(R.string.time, (currentProgress / 60000), (currentProgress / 1000) % 60));
                            progress.setProgress(currentProgress);
                        }
                );
            }

            @Override
            public void newSongEvent(int duration) {
                runOnUiThread(
                        () -> {
                            totalTime.setText(getString(R.string.time, (duration / 60000), (duration / 1000) % 60));
                            progress.setMax(duration);
                        }
                );
            }

            @Override
            public void stopEvent() {
                unbindService(serviceConnection);
                currentPos = -1;
            }

            @Override
            public void currentPosChanged(int currentPosition) {
                currentPos = currentPosition;
            }

        };
    };
    //if play button is clicked, let the service play the music
    public void onClickPlay(View view) {
        if (binderInterface != null) {
            binderInterface.playMusic();
        }
    }
    //if pause button is clicked, let the service pause the music
    public void onClickPause(View view) {
        if (binderInterface != null) {
            binderInterface.pauseMusic();
        }
    }
    //if stop button is clicked, let the service stop the music
    public void onClickStop(View view) {
        if (binderInterface != null) {
            binderInterface.stopMusic();
        }
    }

    //if next button is clicked, play the next song and inform it to service
    public void nextSong(View view) {
        if(currentPos != -1){
            ++currentPos; currentPos %= numOfSong;
            startPlayingMusic();
            Log.d("activity", currentPos + "");
            //binderInterface.nextPos();
        }
    }
    //if prev button is clicked, play the previous song and inform it to service
    public void lastSong(View view) {
        if(currentPos != -1){
            if(--currentPos < 0) currentPos += numOfSong;
            startPlayingMusic();
            Log.d("activity", currentPos + "");
            //binderInterface.prevPos();
        }
    }
    //if loop is clicked, inform the service and reset the icon of the button
    public void loopMethodChange(View view) {
        if (binderInterface != null) {
            if(binderInterface.loopChanged())
                ((Button)view).setCompoundDrawablesWithIntrinsicBounds(R.drawable.loop_list,0,0,0);
            else
                ((Button)view).setCompoundDrawablesWithIntrinsicBounds(R.drawable.repeat_one,0,0,0);
        }
    }

    //start the service regards to the position of the song, so that service got the information needed to play it
    private void startPlayingMusic() {
        Intent mp3Intent = new Intent(MainActivity.this, mp3PlayService.class);
        mp3Intent.putExtra("currentPos", currentPos);
        mp3Intent.putExtra("uris", uris);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(mp3Intent);
            bindService(mp3Intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }
    //if the activity is destroyed, inform service it is no longer bind with it, so no call back
    //methods will be called, and unbind the service to make sure no binding leak
    @Override
    protected void onDestroy() {
        binderInterface.losingConnection();
        Log.d("comp3008", "onDestroy: losing connection");
        if (serviceConnection != null) {
            unbindService(serviceConnection);
            serviceConnection = null;
        }
        Log.d("comp3008", "onDestroy: mainActivity");
        super.onDestroy();
    }
}