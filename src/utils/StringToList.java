package utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xzr on 2018/3/10.
 */

public class StringToList {
    public static ArrayList<String> to(String s){
        ArrayList<String> a=new ArrayList<>();
        if(s.equals(""))
            return a;
        String buffer="";
        int i=0;
        while (true){
            try {
                String c = s.substring(i, i + 1);
                if (c.equals("\n")) {
                    a.add(buffer);
                    buffer = "";
                } else {
                    buffer = buffer+c;
                }
            }
            catch (Exception e){
                a.add(buffer);
                break;
            }
            i++;
        }
        return a;
    }

    public static ArrayList<Integer> string2intList(List<String> input){
        ArrayList<Integer> list=new ArrayList<>();
        for(String s:input){
            try {
                list.add(Integer.parseInt(s));
            }catch (Exception e){
                list.add(-1);
            }
        }
        return list;
    }

    public static ArrayList<String> zram_algorithm(String s){
        ArrayList<String> result=new ArrayList<>();
        int i=0;
        String buffer = "";
        if(!s.endsWith(" "))
            s+=" ";
        while (true){
            String cache;
            try {

                cache = s.substring(i, i + 1);
                if (cache.equals(" ")) {
                    buffer=buffer.replace("[","");
                    buffer=buffer.replace("]","");
                    result.add(buffer);
                    buffer="";
                } else {
                    buffer = buffer + cache;
                }
                i++;
            }
            catch (Exception e){
                break;
            }
        }
        return result;
    }

    public static String getCurrentZramAlgorithm(String s){
        ArrayList<String> result=new ArrayList<>();
        int i=0;
        String buffer = "";
        if(!s.endsWith(" "))
            s+=" ";
        while (true){
            String cache;
            try {

                cache = s.substring(i, i + 1);
                if (cache.equals(" ")) {
                    if(buffer.startsWith("[")&&buffer.endsWith("]")){
                        buffer=buffer.replace("[","");
                        buffer=buffer.replace("]","");
                        return buffer;
                    }
                    result.add(buffer);
                    buffer="";
                } else {
                    buffer = buffer + cache;
                }
                i++;
            }
            catch (Exception e){
                // result.add(buffer);
                break;
            }
        }
        return null;
    }
}
