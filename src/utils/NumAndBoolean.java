package utils;

public class NumAndBoolean {
    //仅仅是一个数字转布尔的转换，适用于部分内核节点
    //仅在传入值为0时返回false
    public static boolean Num2Boolean(String value){
        if(value.equals("0"))
            return false;
        return true;
    }
    public static int Boolean2Num(boolean b){
        if(b)
            return 1;
        return 0;
    }
}
