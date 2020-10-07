package utils;

import java.util.List;

public class LSpinner_helper {
    public static int label2position(List<String> l, String str){
        for(int i=0;i<l.size();i++){
            if(l.get(i).equals(str)){
                return i;
            }
        }
        return 0;
    }
}
