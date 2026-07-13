package net.rs256.flae;

//? if <=1.18.2 {
/*import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
*///?} else {
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
//?}

public final class FLAEMod {
    public static final String MOD_ID = "flae";
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "26.1.2";

    //? if <=1.18.2 {
    /*public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
     *///?} else {
    public static final Logger LOGGER = LogUtils.getLogger();
    //?}

    private FLAEMod() {}
}
