package com.example.mp3player;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteCallbackList;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.Semaphore;
public class mp3PlayService extends Service {
    //semaphores to avoid callback concurrency issue
    private final Semaphore callBackSemaphore = new Semaphore(1);
    //keep a mp3player to do the work
    private final MP3Player myMP3Player = new MP3Player();
    //lists of call back events
    private RemoteCallbackList<MyBinder> remoteCallbackList = new RemoteCallbackList<>();
    //constants
    private final String CHANNEL_ID = "mp3player channel";
    private final int NOTIFICATION_ID = 1;
    //references of the thread to call current progress back
    private Thread musicPlayingThread = null;
    //check if it the activity is connected with the service
    private boolean isConnected;
    //which song it is currently playing
    private int currentPos;
    //uris of all songs
    private String[] uris;
    //check the loop method
    private boolean isRepeating = false;

    //if the service is created then create a channel for further notification
    @Override
    public void onCreate() {
        Log.d("coursework2", "onCreate: mp3");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "music channel",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
        super.onCreate();
    }
    //check the information in the intent and perform appropriate actions.
    //return start redeliver intent in case the OS killed the process. Later on the user
    //can still back to the music he was listening to.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("coursework2", "onStartCommand: mp3");

        if (intent.getExtras() != null) {
            if(intent.getExtras().getStringArray("uris") != null){
                uris = intent.getExtras().getStringArray("uris");
                currentPos = intent.getExtras().getInt("currentPos");
                Log.d("Thread", "current pos "+ currentPos);
                playMusicWithUri(uris[currentPos]);
            }

            if (intent.getExtras().getString("action") != null) {
                Log.d("coursework2", "onStartCommand: got action " + "\"" + intent.getExtras().getString("action") + "\"");
                if (intent.getExtras().getString("action").equals("play")) playMusic();
                else if (intent.getExtras().getString("action").equals("pause")) pauseMusic();
                else if (intent.getExtras().getString("action").equals("stop")) stopMusic();
                else if (intent.getExtras().getString("action").equals("next")) {
                    ++currentPos; currentPos %= uris.length;
                    playMusicWithUri(uris[currentPos]);
                    callPosBack(currentPos);
                }
            }
        }
        return START_REDELIVER_INTENT;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new MyBinder();
    }

    //binder methods for the communication from the activity to the service.
    public class MyBinder extends Binder implements IInterface {

        @Override
        public IBinder asBinder() {
            return this;
        }

        protected void stopMusic() {
            mp3PlayService.this.stopMusic();
        }

        protected void pauseMusic() {
            mp3PlayService.this.pauseMusic();
        }

        protected void playMusic() {
            mp3PlayService.this.playMusic();
        }
        //seek to user set time
        protected void seekbarChanged(int timeToSeek) {
            myMP3Player.setProgress(timeToSeek);
        }

        public int totalTime() {
            return myMP3Player.getDuration();
        }

        public void losingConnection(){isConnected = false;}

        public void gainingConnection(){ isConnected = true; callPosBack(currentPos); }

        public boolean isRepeating(){return isRepeating;}

        public int getCurrentProgress(){return myMP3Player.getProgress();}

        public void registerCallback(ICallback callback) {
            this.callback = callback;
            remoteCallbackList.register(MyBinder.this);
        }

        public void unregisterCallback(ICallback callback) {
            remoteCallbackList.unregister(MyBinder.this);
        }

        protected boolean loopChanged(){
            isRepeating = !isRepeating;
            return !isRepeating;
        }

        protected ICallback callback;
    }

    //build a notification, user can navigate back to the activity by clicking it, or they can perform actions
    //such as play the song, pause the song, play the next song, and stop the song(stop the service)
    private NotificationCompat.Builder myNotificationBuilder(String songName) {
        final int ACTIVITY_REQUEST_CODE = 0;
        final int PLAY_REQUEST_CODE = 1;
        final int PAUSE_REQUEST_CODE = 2;
        final int STOP_REQUEST_CODE = 3;
        final int NEXT_REQUEST_CODE = 4;

        Intent getBackToActivityIntent = new Intent(mp3PlayService.this, MainActivity.class);
        getBackToActivityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK |Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent getBackToActivityPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getBackToActivityPendingIntent = PendingIntent.getActivity(this, ACTIVITY_REQUEST_CODE, getBackToActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }else{
            getBackToActivityPendingIntent = PendingIntent.getActivity(this, ACTIVITY_REQUEST_CODE, getBackToActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }


        Intent playIntent = new Intent(mp3PlayService.this, mp3PlayService.class).putExtra("action", "play");
        Intent pauseIntent = new Intent(mp3PlayService.this, mp3PlayService.class).putExtra("action", "pause");
        Intent stopIntent = new Intent(mp3PlayService.this, mp3PlayService.class).putExtra("action", "stop");
        Intent nextIntent = new Intent(mp3PlayService.this, mp3PlayService.class).putExtra("action", "next");
        PendingIntent playPendingIntent,pausePendingIntent,stopPendingIntent,nextPendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        playPendingIntent =
                PendingIntent.getService(this, PLAY_REQUEST_CODE, playIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        pausePendingIntent =
                PendingIntent.getService(this, PAUSE_REQUEST_CODE, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        stopPendingIntent =
                PendingIntent.getService(this, STOP_REQUEST_CODE, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        nextPendingIntent =
                PendingIntent.getService(this, NEXT_REQUEST_CODE, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            playPendingIntent =
                    PendingIntent.getService(this, PLAY_REQUEST_CODE, playIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            pausePendingIntent =
                    PendingIntent.getService(this, PAUSE_REQUEST_CODE, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            stopPendingIntent =
                    PendingIntent.getService(this, STOP_REQUEST_CODE, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            nextPendingIntent =
                    PendingIntent.getService(this, NEXT_REQUEST_CODE, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.audio_note)
                .setContentTitle("My Mp3 Player")
                .setContentText(songName)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(getBackToActivityPendingIntent)
                .addAction(myMP3Player.getState() == MP3Player.MP3PlayerState.PAUSED? R.drawable.play_arrow : R.drawable.pause,
                        myMP3Player.getState() == MP3Player.MP3PlayerState.PAUSED?"Play":"Pause",
                        myMP3Player.getState() == MP3Player.MP3PlayerState.PAUSED?playPendingIntent:pausePendingIntent)
                .addAction(R.drawable.skip_next_24, "Next", nextPendingIntent)
                .addAction(R.drawable.stop, "Stop", stopPendingIntent)
                .setOnlyAlertOnce(true);
    }
    //constantly call the current progress back, if the song has finished, call another thread to
    //do the mission regards to the looping state to avoid concurrency issue
    private Thread musicPlayThreadCreator() {
        return new Thread(() -> {
            while (myMP3Player.getState() == MP3Player.MP3PlayerState.PLAYING) {
                try {
                    Thread.sleep(1000);
                    callCurrentTimeBack(myMP3Player.getProgress());

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if(myMP3Player.getState() == MP3Player.MP3PlayerState.FINISHED){
                new Thread(this::checkLoop).start();
            }
            Log.d("com2003", "music play thread ending.....");
        });
    }
    //check the state of the loop, and perform actions
    private void checkLoop(){
        if(isRepeating){
            playMusic();
            Log.d("com2003", "checkLoop: looping");
        } else{
            ++currentPos; currentPos %= uris.length;
            playMusicWithUri(uris[currentPos]);
            callPosBack(currentPos);
            Log.d("com2003", "checkLoop: looping");
        }
    }

    //play the music with the given uri
    private void playMusicWithUri(String uri){
        if (musicPlayingThread != null && musicPlayingThread.isAlive() && !uri.equals( myMP3Player.getFilePath())) {
            Log.d("coursework2", "interrupted");
            musicPlayingThread.interrupt();
        }
        if (myMP3Player.load(uri)) {
            startForeground(NOTIFICATION_ID, myNotificationBuilder(myMP3Player.getSongName()).build());
            callDurationBack(myMP3Player.getDuration());
            musicPlayingThread = musicPlayThreadCreator();
            musicPlayingThread.start();
        }
    }
    //recall and perform all the change if actions are performed successfully
    private void stopMusic() {
        if (myMP3Player.stop()) {
            Log.d("comp3008", "stopMusic: service destroyed?");
            musicPlayingThread.interrupt();
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
            stopForeground(true);
            callPosBack(-1);
            callStopBack();
            callDurationBack(0);
            callCurrentTimeBack(0);
            this.stopSelf();
        }
    }

    private void pauseMusic() {
        if (myMP3Player.pause()) {
            musicPlayingThread.interrupt();
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);

            startForeground(NOTIFICATION_ID, myNotificationBuilder("Paused").build());
        }
    }

    private void playMusic() {
        if (myMP3Player.play()) {
            Log.d("coursework2", "from pause to play");
            musicPlayingThread = musicPlayThreadCreator();
            musicPlayingThread.start();
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
            startForeground(NOTIFICATION_ID, myNotificationBuilder("Now Playing: " + myMP3Player.getSongName()).build());
        }
    }

    //call the current progress back
    private void callCurrentTimeBack(int currentTime) {
        if(isConnected){
            try {
                callBackSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            remoteCallbackList.beginBroadcast();
            if(remoteCallbackList.getRegisteredCallbackCount() > 0)
                remoteCallbackList.getBroadcastItem(0).callback.playingEvent(currentTime);
            remoteCallbackList.finishBroadcast();
            callBackSemaphore.release();
        }
    }
    //call the current duration back
    private void callDurationBack(int duration) {
        if(isConnected){
            try {
                callBackSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            remoteCallbackList.beginBroadcast();
            if(remoteCallbackList.getRegisteredCallbackCount() > 0)
                remoteCallbackList.getBroadcastItem(0).callback.newSongEvent(duration);
            remoteCallbackList.finishBroadcast();
            callBackSemaphore.release();
        }
    }
    //inform the activity if the service is stopped
    private void callStopBack() {
        if(isConnected){
            try {
                callBackSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d("Comp3008", "callStopBack: it is connected");
            remoteCallbackList.beginBroadcast();
            if(remoteCallbackList.getRegisteredCallbackCount() > 0)
                remoteCallbackList.getBroadcastItem(0).callback.stopEvent();
            remoteCallbackList.finishBroadcast();
            callBackSemaphore.release();
        }
    }
    //inform the activity if the song currently playing is not the previous one
    private void callPosBack(int pos){
        if(isConnected){
            try {
                callBackSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            remoteCallbackList.beginBroadcast();
            if(remoteCallbackList.getRegisteredCallbackCount() > 0)
                remoteCallbackList.getBroadcastItem(0).callback.currentPosChanged(pos);
            remoteCallbackList.finishBroadcast();
            callBackSemaphore.release();
        }
    }
    //if the service is destroyed, cancel all notifications and delete the channel
    @Override
    public void onDestroy() {
        Log.d("coursework", "onDestroy: service");
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).deleteNotificationChannel(CHANNEL_ID);
        }
        stopForeground(true);
        super.onDestroy();
    }


}
