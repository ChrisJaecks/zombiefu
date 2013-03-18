package zombiefu.actor;

import jade.fov.ViewField;
import jade.util.Dice;
import jade.util.Guard;
import jade.util.datatype.ColoredChar;
import jade.util.datatype.Coordinate;
import jade.util.datatype.Direction;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import zombiefu.human.Human;
import zombiefu.monster.Monster;
import zombiefu.exception.CannotMoveToIllegalFieldException;
import zombiefu.exception.WeaponHasNoMunitionException;
import zombiefu.items.Weapon;
import zombiefu.items.WeaponType;
import zombiefu.util.DamageAnimation;
import zombiefu.exception.NoDirectionGivenException;
import zombiefu.player.Attribute;
import zombiefu.player.Discipline;
import zombiefu.player.Player;
import zombiefu.util.ZombieGame;
import zombiefu.util.ZombieTools;

public abstract class Creature extends NotPassableActor {

    private static final double EXPERT_BONUS = 1.5; // Faktor
    protected HashMap<Attribute, Integer> attributSet;
    protected Discipline discipline;
    protected int dazed;
    protected int healthPoints;
    protected String name;
    protected ViewField fov;
    protected int sichtweite;
    protected boolean godMode;

    public static final HashMap<Attribute, Integer> getDefaultAttributeSet() {
        HashMap<Attribute, Integer> attSet = new HashMap<>();
        for (Attribute att : Attribute.values()) {
            attSet.put(att, 1);
        }
        return attSet;
    }

    public Creature(ColoredChar face, String n, HashMap<Attribute, Integer> a) {
        super(face);
        dazed = 0;
        name = n;
        attributSet = a;
        healthPoints = a.get(Attribute.MAXHP);
    }

    public Creature(ColoredChar face, String n) {
        this(face, n, getDefaultAttributeSet());
    }

    public Discipline getDiscipline() {
        return discipline;
    }

    public boolean isGod() {
        return godMode;
    }

    public int getAttribute(Attribute att) {
        return attributSet.get(att);
    }

    public int getHealthPoints() {
        return healthPoints;
    }

    public Collection<Coordinate> getViewField() {
        return fov.getViewField(world(), pos(), sichtweite);
    }

    public abstract Weapon getActiveWeapon();

    @Override
    public void setPos(int x, int y) {
        if (world().passableAt(x, y)) {
            super.setPos(x, y);
        }
    }

    public String getName() {
        return name;
    }

    protected abstract Direction getAttackDirection()
            throws NoDirectionGivenException;

    public void hurtCreature(Creature cr, double faktor) {

        // Wer keine Weapon hat, kann nicht angreifen!
        if (getActiveWeapon() == null) {
            return;
        }

        ZombieTools.log("hurtCreature(): " + getName() + " hurts "
                + cr.getName() + " with " + getActiveWeapon().getName()
                + " (Damage: " + getActiveWeapon().getDamage()
                + ", Experte: " + getActiveWeapon().isExpert(discipline) + "). Attack value: " + getAttribute(Attribute.ATTACK) + ", Defense Value: "
                + cr.getAttribute(Attribute.DEFENSE) + ", Faktor: " + faktor);

        // Calculate damage
        int damage = (int) (((double) getActiveWeapon().getDamage())
                * ((double) getAttribute(Attribute.ATTACK) / (double) cr.getAttribute(Attribute.DEFENSE))
                * (double) Dice.global.nextInt(20, 40) / 30 * faktor * (getActiveWeapon().isExpert(discipline) ? EXPERT_BONUS : 1.0));
        if (damage == 0) {
            damage = 1;
        }

        ZombieGame.newMessage(getName() + " hat " + cr.getName() + " " + damage
                + " Schadenspunkte hinzugefügt.");

        cr.hurt(damage, this);
    }

    public void hurtCreature(Creature cr) {
        hurtCreature(cr, 1);
    }

    public void attackCoordinate(Coordinate coord) {
        Guard.argumentIsNotNull(coord);
        DamageAnimation anim = new DamageAnimation();
        world().addActor(anim, coord);
        Collection<Creature> actors = world()
                .getActorsAt(Creature.class, coord);
        if (actors.isEmpty()) {
            ZombieGame.newMessage("Niemanden getroffen!");
        } else {
            Iterator<Creature> it = actors.iterator();
            while (it.hasNext()) {
                hurtCreature(it.next());
            }
        }
        world().removeActor(anim);
        anim.expire();
    }

    private void createDetonation(Coordinate c, double blastRadius,
            boolean includeCenter) {
        // TODO: Verschönern (mit RayCaster)
        Collection<Creature> targets = new HashSet<Creature>();
        Collection<DamageAnimation> anims = new HashSet<DamageAnimation>();
        int blastMax = (int) Math.ceil(blastRadius);
        for (int x = Math.max(0, c.x() - blastMax); x <= Math.min(c.x()
                + blastMax, world().width() - 1); x++) {
            for (int y = Math.max(0, c.y() - blastMax); y <= Math.min(c.y()
                    + blastMax, world().height() - 1); y++) {
                Coordinate neu = new Coordinate(x, y);
                if (neu.distance(c) <= blastRadius
                        && (includeCenter || !c.equals(neu))) {
                    DamageAnimation anim = new DamageAnimation();
                    anims.add(anim);
                    world().addActor(anim, neu);
                    Collection<Creature> actors = world().getActorsAt(
                            Creature.class, neu);
                    Iterator<Creature> it = actors.iterator();
                    while (it.hasNext()) {
                        Creature next = it.next();
                        targets.add(next);
                    }
                }
            }
        }
        if (targets.isEmpty()) {
            ZombieGame.newMessage("Niemanden getroffen!");
        } else {
            for (Creature target : targets) {
                hurtCreature(target);
            }
        }
        for (DamageAnimation anim : anims) {
            world().removeActor(anim);
            anim.expire();
        }

    }

    private Coordinate findTargetInDirection(Direction dir, int maxDistance) {
        Coordinate nPos = pos();
        int dcounter = 0;
        do {
            nPos = nPos.getTranslated(dir);
            if (!world().insideBounds(nPos) || !world().passableAt(nPos)) {
                return nPos
                        .getTranslated(ZombieTools.getReversedDirection(dir));
            }
            dcounter++;
        } while (world().getActorsAt(NotPassableActor.class, nPos).isEmpty()
                && dcounter < maxDistance);
        return nPos;
    }

    public void attack(Direction dir) throws WeaponHasNoMunitionException {
        WeaponType typ = getActiveWeapon().getTyp();
        getActiveWeapon().useMunition();
        Coordinate ziel;
        if (typ.isRanged()) {
            ziel = findTargetInDirection(dir, getActiveWeapon().getRange());
        } else {
            ziel = pos().getTranslated(dir);
        }
        if (typ.isDirected()) {
            attackCoordinate(ziel);
        } else {
            createDetonation(ziel, getActiveWeapon().getBlastRadius(),
                    typ.isRanged());
        }
    }

    public void attack() throws NoDirectionGivenException,
            WeaponHasNoMunitionException {
        Direction dir;
        if (getActiveWeapon().getTyp() != WeaponType.UMKREIS) {
            dir = getAttackDirection();
        } else {
            dir = Direction.ORIGIN;
        }
        attack(dir);
    }

    public void tryToMove(Direction dir)
            throws CannotMoveToIllegalFieldException,
            WeaponHasNoMunitionException {
        Guard.argumentIsNotNull(world());
        Guard.argumentIsNotNull(dir);
        if (dazed > 0) {
            dazed--;
            return;
        }
        if (dir == Direction.ORIGIN) {
            return;
        }
        Coordinate targetField = pos().getTranslated(dir);
        if (!world().insideBounds(targetField)
                || !world().passableAt(targetField)) {
            throw new CannotMoveToIllegalFieldException();
        }

        NotPassableActor actor = world().getActorAt(NotPassableActor.class,
                pos().getTranslated(dir));
        if (actor == null) {
            move(dir);
            return;
        }

        if (this instanceof Player) {
            if (actor instanceof Door) {
                if (isGod()) {
                    ((Door) actor).open();
                } else {
                    ZombieGame.newMessage("Diese Tür ist geschlossen. Du brauchst einen Schlüssel um sie zu öffnen");
                    throw new CannotMoveToIllegalFieldException();
                }
            } else if (actor instanceof Human) {
                ((Human) actor).talkToPlayer((Player) this);
                return;
            } else if (!(actor instanceof Monster)) {
                throw new CannotMoveToIllegalFieldException();

            }
        }

        if (this instanceof Monster && !(actor instanceof Player)) {
            throw new CannotMoveToIllegalFieldException();
        }

        if (getActiveWeapon().getTyp() == WeaponType.NAHKAMPF) {
            attack(dir);
        } else {
            if (this instanceof Player) {
                ZombieGame.newMessage("Du trägst keine Nahkampfwaffe!");
            }
            throw new CannotMoveToIllegalFieldException();
        }
    }

    public abstract void killed(Creature killer);

    private void hurt(int i, Creature hurter) {
        ZombieTools.log(getName() + " hat " + i + " HP verloren. ");
        if (godMode) {
            return;
        }
        if (i >= healthPoints) {
            killed(hurter);
        } else {
            healthPoints -= i;
        }
    }
}
