package com.example.mp3player;
//call back interface
public interface ICallback {
    public void playingEvent(int currentProgress);
    public void newSongEvent(int duration);
    public void stopEvent();
    public void currentPosChanged(int currentPosition);
}
