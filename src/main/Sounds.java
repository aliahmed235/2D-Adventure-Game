package main;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.net.URI;
import java.net.URL;

public class Sounds {
    Clip clip;
    URL soundURI[] = new URL[30];


    public Sounds(){
        soundURI[0] = getClass().getResource("/sounds/BlueBoyAdventure.wav");
        soundURI[1] = getClass().getResource("/sounds/coin.wav");
        soundURI[2] = getClass().getResource("/sounds/powerup.wav");
        soundURI[3] = getClass().getResource("/sounds/unlock.wav");
        soundURI[4] = getClass().getResource("/sounds/fanfare.wav");
    }

    public void setFile(int i){
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(soundURI[i]);
            clip = AudioSystem.getClip();
            clip.open(ais);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    public  void play(){

        clip.start();

    }
    public void loop(){
        clip.loop(Clip.LOOP_CONTINUOUSLY);
    }
    public  void stop(){
        clip.stop();
    }
}
