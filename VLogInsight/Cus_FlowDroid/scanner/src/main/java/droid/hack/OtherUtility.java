package droid.hack;

import soot.jimple.*;

public class OtherUtility {
    public static boolean isStrConstant(Object obj) {
        return obj instanceof StringConstant;
    }

    public static boolean isNumConstant(Object obj) {
        return obj instanceof IntConstant || obj instanceof LongConstant || obj instanceof FloatConstant || obj instanceof DoubleConstant;
    }

    public static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        }

        catch (NumberFormatException er) {
            return false;
        }
    }

    public static int string2Int(String i) {
        return Integer.parseInt(i);
    }

    public static long string2Long(String i) {
        return Long.parseLong(i);
    }

    public static float string2Float(String i) {
        return Float.parseFloat(i);
    }

    public static double string2Double(String i) {
        return Double.parseDouble(i);
    }

}
