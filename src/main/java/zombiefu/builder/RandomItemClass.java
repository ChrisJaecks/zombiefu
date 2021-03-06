/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package zombiefu.builder;

import zombiefu.items.Item;
import zombiefu.items.MensaCard;
import zombiefu.util.ConfigHelper;
import zombiefu.util.ZombieGame;
import zombiefu.util.ZombieTools;

/**
 *
 * @author tomas
 */
public enum RandomItemClass {

    RARE,
    AWESOME,
    GOOD,
    COMMON,
    JUNK;

    public static RandomItemClass fromString(String string) {
        for (RandomItemClass d : RandomItemClass.values()) {
            if (d.toString().equals(string.toUpperCase())) {
                return d;
            }
        }
        throw new IllegalArgumentException("Ungültiger RandomItemGenerator-Name: " + string);
    }

    public MensaCard getMensacard(int baseValue) {
        int value = (int) (baseValue * (ZombieGame.getPlayer().getSemester() + 1) * ZombieTools.getRandomDouble(0.5, 2.0) / 2);
        if (value < 1) {
            value = 1;
        }
        return new MensaCard(value);
    }

    public Item newRandomItem() {
        return ConfigHelper.newRandomItem(this);
    }

   
}
