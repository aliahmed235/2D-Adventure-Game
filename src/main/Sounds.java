package main;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;

public class Sounds {
    Clip clip;
    String soundPath[] = new String[30];


    public Sounds(){
        soundPath[0] = "res/sounds/BlueBoyAdventure.wav";
        soundPath[1] = "res/sounds/coin.wav";
        soundPath[2] = "res/sounds/powerup.wav";
        soundPath[3] = "res/sounds/unlock.wav";
        soundPath[4] = "res/sounds/fanfare.wav";
    }

    public void setFile(int i){
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(new File(soundPath[i]));
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