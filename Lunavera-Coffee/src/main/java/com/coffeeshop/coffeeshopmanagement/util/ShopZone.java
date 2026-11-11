package com.coffeeshop.coffeeshopmanagement.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The time zone the shop keeps its books in (TODO 3.68). Every date in the database is zone-less text, so the program must write the
 * same zone on every till; it does not trust the PC's zone, which a fresh Windows install, a laptop back from a trip or an
 * "automatic zone" can leave on another country. {@link #install} hands the shop's zone to {@link AppClock}, and
 * {@link #mismatchWarning} tells the owner at start when the PC is set to another one, with both times, so a PC whose <em>clock</em>
 * was set by hand to local time under the wrong zone is noticed too.
 *
 * <p>The zone is {@link #DEFAULT} (Vietnam has no summer time). A shop elsewhere sets {@code shop.zone=<zone id>} in
 * {@code ui-settings.properties} in the data folder; an unknown id is ignored with a log line. It is kept per computer, not in the
 * database, because {@code AppClock} is read before the database is open; the tills of a shop are in one country.
 */
public final class ShopZone {

    private static final Logger LOGGER = Logger.getLogger(ShopZone.class.getName());

    public static final ZoneId DEFAULT = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String KEY = "shop.zone";

    private ShopZone() {
    }

    /**
     * The zone to use for a configured text: the zone with that id, or {@link #DEFAULT} when the text is empty or not a zone.
     */
    public static ZoneId resolve(String configured) {
        if (configured == null || configured.isBlank()) return DEFAULT;
        try {
            return ZoneId.of(configured.trim());
        } catch (java.time.DateTimeException e) {
            LOGGER.log(Level.WARNING, "Unknown shop time zone \"" + configured + "\"; using " + DEFAULT);
            return DEFAULT;
        }
    }

    /** Reads the shop's zone and gives it to {@link AppClock}. Called once, first thing at start-up; never throws. */
    public static void install() {
        ZoneId zone = DEFAULT;
        try {
            zone = resolve(UiSettings.get(KEY, ""));
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not read the shop time zone; using " + DEFAULT, e);
        }
        AppClock.setZone(zone);
    }

    /**
     * The warning for the start-up when the PC's zone gives another time than the shop's zone, or null when the two agree at
     * {@code instant}. Two zones with the same offset (Bangkok and Ho Chi Minh) agree; a zone that is the same in name but
     * is off by an hour for part of the year does not.
     */
    public static String mismatchWarning(ZoneId pc, ZoneId shop, Instant instant) {
        if (pc.getRules().getOffset(instant).equals(shop.getRules().getOffset(instant))) return null;
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
        LocalDateTime onPc = LocalDateTime.ofInstant(instant, pc);
        LocalDateTime inShop = LocalDateTime.ofInstant(instant, shop);
        return I18n.t("app.zone.mismatch", describe(pc, instant), describe(shop, instant), onPc.format(time), inShop.format(time));
    }

    /** "Asia/Ho_Chi_Minh (UTC+07:00)". */
    static String describe(ZoneId zone, Instant instant) {
        String offset = zone.getRules().getOffset(instant).getId();
        return zone.getId() + " (UTC" + ("Z".equals(offset) ? "+00:00" : offset) + ")";
    }
}
