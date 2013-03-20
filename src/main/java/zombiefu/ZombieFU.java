package zombiefu;

import zombiefu.util.ZombieGame;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ZombieFU extends JApplet {

    public static void main(String[] args) {

        ZombieGame.createGame(args, "The Final Exam - Die Anwesenheitspflicht schlägt zurück");
        ZombieGame.showStaticImage("startscreen");
        ZombieGame.showStaticImage("story");
        ZombieGame.initialize();

        ZombieGame.startGame();

    }

    public void init() {
        System.out.println("Hallo!");
    }

    public void destroy() {
        System.out.println("Bye!");
    }


}
