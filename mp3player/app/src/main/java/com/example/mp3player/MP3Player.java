package com.example.mp3player;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;

/**
 * Created by pszmdf on 06/11/16.
 */
public class MP3Player {

    private MediaPlayer mediaPlayer;
    private MP3PlayerState state;
    private String filePath = "";
    private String songName;

    public String getSongName() {
        return songName;
    }

    public enum MP3PlayerState {
        ERROR,
        PLAYING,
        PAUSED,
        STOPPED,
        FINISHED
    }

    public MP3Player() {
        this.state = MP3PlayerState.STOPPED;
    }

    public MP3PlayerState getState() {
        return this.state;
    }
    //return a boolean value if the song is loaded correctly
    public boolean load(String filePath) {
        //if the song is currently played
        if ((mediaPlayer != null) && (getState() == MP3PlayerState.PLAYING) && filePath.equals(getFilePath())) {
            Log.d("coursework2", "The song is currently playing");
            return false;
        }

        //if new song wants to be loaded, we need to reset the media player

        if(mediaPlayer != null)mediaPlayer.stop();
        state = MP3PlayerState.STOPPED;
        if(mediaPlayer != null) mediaPlayer.reset();
        if(mediaPlayer != null)mediaPlayer.release();
        mediaPlayer = null;


        this.filePath = filePath;
        this.songName = getFilePath().split("/")[(getFilePath().split("/").length) - 1];

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        try {
            mediaPlayer.setDataSource(filePath);
            Log.d("MP3Player", "load: " + filePath);
            mediaPlayer.prepare();
        } catch (IOException | IllegalArgumentException e) {
            Log.e("MP3Player", e.toString());
            e.printStackTrace();
            this.state = MP3PlayerState.ERROR;
            return false;
        }

        this.state = MP3PlayerState.PLAYING;
        mediaPlayer.setOnCompletionListener(mediaPlayer -> {
            Log.d("MP3Player", "the song has been completed");
            state = MP3PlayerState.FINISHED;
        });
        mediaPlayer.start();
        return true;
    }

    public String getFilePath() {
        return this.filePath;
    }
    //if user want to change where they want to listen the song
    public void setProgress(int timeToSeek) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(timeToSeek);
        }
    }

    public int getProgress() {
        if (mediaPlayer != null) {
            if (this.state == MP3PlayerState.PAUSED || this.state == MP3PlayerState.PLAYING)
                return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null)
            if (this.state == MP3PlayerState.PAUSED || this.state == MP3PlayerState.PLAYING)
                return mediaPlayer.getDuration();
        return 0;
    }
    //return a bool if it is played correctly
    public boolean play() {
        if (this.state == MP3PlayerState.PAUSED || this.state == MP3PlayerState.FINISHED) {
            if (this.state == MP3PlayerState.FINISHED)
                mediaPlayer.seekTo(0);
            mediaPlayer.start();
            this.state = MP3PlayerState.PLAYING;
            return true;
        }
        return false;
    }
    //return a bool if it is paused correctly
    public boolean pause() {
        if (this.state == MP3PlayerState.PLAYING) {
            mediaPlayer.pause();
            state = MP3PlayerState.PAUSED;
            return true;
        }
        return false;
    }
    //return a bool if it is stopped correctly
    public boolean stop() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying())
                mediaPlayer.stop();
            state = MP3PlayerState.STOPPED;
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
            return true;
        }
        return false;
    }

}