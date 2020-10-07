import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import utils.LSpinner_helper;
import utils.StringToList;
import xzr.La.systemtoolbox.modules.java.LModule;
import xzr.La.systemtoolbox.ui.StandardCard;
import xzr.La.systemtoolbox.ui.views.LSpinner;
import xzr.La.systemtoolbox.utils.process.ShellUtil;

import java.util.ArrayList;
import java.util.List;

public class ZRAM implements LModule {
    final String ZRAM_CONTROL_PATH="/sys/class/zram-control";
    final String ZRAM_CONF_PATH="/sys/block/zram";
    final String ZRAM_DEV_PATH="/dev/block/zram";
    final String VM_PATH="/proc/sys/vm";
    final int MB2B=1024*1024;

    LinearLayout inside;
    AlertDialog dia;
    AlertDialog wait_dia;

    List<Integer> all_blocks; //所有已创建的区块
    List<Integer> registered_blocks; //所有已注册的区块
    List<Integer> enabled_blocks; //所有已启用的区块

    static String busybox_awk="";
    @Override
    public String classname() {
        return "io";
    }

    @Override
    public View init(Context context) {
        if(incompatible())
            return null;

        LinearLayout linearLayout=new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        TextView title= StandardCard.title(context);
        TextView subtitle=StandardCard.subtitle(context);
        title.setText("ZRAM控制");
        subtitle.setText("控制内存压缩区的状态");
        linearLayout.addView(title);
        linearLayout.addView(subtitle);

        LinearLayout settings=new LinearLayout(context);
        linearLayout.addView(settings);
        if(support_zram_control())
            settings.addView(new Button(context){{
                setText("新区块");
                setBackgroundColor(android.R.attr.buttonBarButtonStyle);
                setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ShellUtil.run("cat "+ZRAM_CONTROL_PATH+"/hot_add",true);
                        ((Activity)context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                genInsideView(context);
                            }
                        });
                    }
                });
            }});
        //通用设置
        settings.addView(new Button(context){{
            setText("通用设置");
            setBackgroundColor(android.R.attr.buttonBarButtonStyle);
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    show_generic_dialog(context);
                }
            });
        }});
        {
            TextView textView=StandardCard.subtitle(context);
            textView.setText("已存在的区块：");
            linearLayout.addView(textView);
        }
        inside=new LinearLayout(context);
        inside.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(inside);

        genInsideView(context);


        return linearLayout;
    }

    String gen_vm_path(String node){
        return VM_PATH+"/"+node;
    }

    String write_cmd(String path,String value){
        return "echo \""+value+"\" > "+path;
    }

    void show_generic_dialog(Context context){
        final String SWAPPINESS="swappiness";

        ScrollView scrollView=new ScrollView(context);
        LinearLayout linearLayout=new LinearLayout(context);
        scrollView.addView(linearLayout);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        {
            TextView textView=StandardCard.subtitle(context);
            textView.setText("Swappiness:");
            linearLayout.addView(textView);
        }

        EditText swappiness=new EditText(context);
        linearLayout.addView(swappiness);
        swappiness.setInputType(InputType.TYPE_CLASS_NUMBER);
        swappiness.setText(ShellUtil.run("cat "+gen_vm_path(SWAPPINESS),true));

        {
            TextView textView=new TextView(context);
            linearLayout.addView(textView);
            textView.setText("* 在一般内核上，swappiness的取值范围是0到100，部分魔改的第三方内核可能允许更大的取值量。" +
                    "swappiness代表了系统在回收内存时是优先回收文件页面，还是优先使用swap/zram。" +
                    "0代表仅在内存严重不足时使用swap/zram，100则代表文件页面与swap/zram在内存回收时地位等同。" +
                    "你不需要了解这么多，你可以将其粗略的理解为swap/zram的使用激进程度。");
        }
        new AlertDialog.Builder(context)
                .setTitle("通用设置")
                .setView(scrollView)
                .setNegativeButton("取消",null)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ShellUtil.run(write_cmd(gen_vm_path(SWAPPINESS),swappiness.getText().toString()),true);
                    }
                }).create().show();
    }

    void genInsideView(Context context){
        inside.removeAllViews();
        all_blocks=StringToList.string2intList(gen_all_zram_blocks());
        registered_blocks=StringToList.string2intList(gen_registered_zram_blocks());
        enabled_blocks=StringToList.string2intList(gen_enabled_zram_blocks());

        for(int index:all_blocks){
            if(index==-1)
                continue;
            Button button=new Button(context);
            button.setBackgroundColor(android.R.attr.buttonBarButtonStyle);
            button.setText("ZRAM区块"+index);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showDialog(index,context);
                }
            });
            inside.addView(button);
        }
    }

    void showDialog(int index,Context context){
        wait_dia=new AlertDialog.Builder(context)
                .setTitle("请稍后")
                .setView(new LinearLayout(context){{
                    addView(new ProgressBar(context));
                    addView(new TextView(context){{setText("正在应用请求");}});
                }})
                .create();

        ScrollView scrollView=new ScrollView(context);

        LinearLayout linearLayout=new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout);

        long disksize=-1;
        try{
            disksize=Long.parseLong(ShellUtil.run("cat "+gen_conf_path(index)+"/disksize",true));
        }catch (Exception e){
        }

        if(disksize>=0) {
            TextView textView=StandardCard.subtitle(context);
            textView.setText("区块大小："+disksize/MB2B+"MB");
            if(!is_registered(index)&&disksize==0) {
                textView.append(" （点击编辑）");
                textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (!is_registered(index)) {
                            EditText editText = new EditText(context);
                            editText.setHint("区块大小(MB)");
                            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                            new AlertDialog.Builder(context)
                                    .setTitle("编辑区块大小")
                                    .setMessage("输入区块的新大小(MB)\n注意：这里的区块大小是指允许压缩的最大内存原始大小（压缩前大小）\n" +
                                            "在设置区块的大小后，您需要反注册或重置此区块，才能再次调整其大小。")
                                    .setView(editText)
                                    .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            int size;
                                            try {
                                                size = Integer.parseInt(editText.getText().toString());
                                                if (size <= 0)
                                                    throw new Exception();
                                            } catch (Exception e) {
                                                Toast.makeText(context, "无效的输入", Toast.LENGTH_SHORT).show();
                                                return;
                                            }

                                            ShellUtil.run("echo " + (long) size * MB2B + " > " + gen_conf_path(index) + "/disksize",true);
                                            dia.dismiss();

                                            ((Activity) context).runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    genInsideView(context);
                                                }
                                            });
                                        }
                                    })
                                    .setNegativeButton("取消", null)
                                    .create().show();
                        }
                    }
                });
            }
            linearLayout.addView(textView);
        }

        {
            TextView textView=new TextView(context);
            textView.setText("状态：");
            linearLayout.addView(textView);
            if(is_registered(index))
                textView.setText(textView.getText().toString()+"已注册 ");
            else
                textView.setText(textView.getText().toString()+"未注册 ");
            if(is_enabled(index))
                textView.setText(textView.getText().toString()+"已启用");
            else
                textView.setText(textView.getText().toString()+"未启用");
        }

        long orig_data_size=-1;
        if(support_mm_stat(index)) {
            try {
                orig_data_size = Long.parseLong(ShellUtil.run("cat " + gen_conf_path(index) + "/mm_stat|"+busybox_awk+"awk '{print $1}'", true));
            } catch (Exception e) {
            }
        }

        if(orig_data_size>=0){
            TextView textView=StandardCard.subtitle(context);
            textView.setText("已存储数据的压缩前大小："+orig_data_size/MB2B+"MB");
            linearLayout.addView(textView);
        }

        long mem_used_total=-1;
        if(support_mm_stat(index)) {
            try {
                mem_used_total = Long.parseLong(ShellUtil.run("cat " + gen_conf_path(index) + "/mm_stat|"+busybox_awk+"awk '{print $3}'", true));
            } catch (Exception e) {
            }
        }

        if(mem_used_total>=0){
            TextView textView=StandardCard.subtitle(context);
            textView.setText("已存储数据的压缩后大小："+mem_used_total/MB2B+"MB");
            linearLayout.addView(textView);
        }

        if(disksize>0 && orig_data_size>=0){
            TextView textView=StandardCard.subtitle(context);
            textView.setText("使用率："+100*orig_data_size/disksize+"%");
            linearLayout.addView(textView);
        }

        if(orig_data_size>=0 && mem_used_total>0){
            TextView textView=StandardCard.subtitle(context);
            textView.setText("压缩比："+(float)orig_data_size/mem_used_total);
            linearLayout.addView(textView);
        }

        if(is_enabled(index)){
            TextView textView=StandardCard.subtitle(context);
            textView.setText("优先级："+ShellUtil.run("cat /proc/swaps | grep zram"+index+" | grep -o \"[0-9]*$\"",true));
            linearLayout.addView(textView);
        }

        {
            TextView textView=new TextView(context);
            textView.setText("* 区块大小指的是允许压缩的最大内存原始大小，而非压缩后大小。");
            linearLayout.addView(textView);
        }

        TextView algorithm_text=StandardCard.subtitle(context);
        algorithm_text.setText("压缩算法：");
        linearLayout.addView(algorithm_text);

        //压缩算法调整
        String raw_comp_algorithm=ShellUtil.run("cat "+ZRAM_CONF_PATH+index+"/comp_algorithm",true);
        if(!is_registered(index)&&disksize==0) {
            List<String> algorithms = StringToList.zram_algorithm(raw_comp_algorithm);
            LSpinner algorithm = new LSpinner(context, algorithms);
            linearLayout.addView(algorithm);
            algorithm.setSelection(LSpinner_helper.label2position(algorithms, StringToList.getCurrentZramAlgorithm(raw_comp_algorithm)));
            algorithm.setOnItemClickListener(new LSpinner.OnItemClickListener() {
                @Override
                public void onClick(int i) {
                    ShellUtil.run(write_cmd(ZRAM_CONF_PATH + index + "/comp_algorithm", algorithm.getSelectedLabel()), true);
                }
            });
        }else
            algorithm_text.append(StringToList.getCurrentZramAlgorithm(raw_comp_algorithm));

        if(is_registered(index))
        if(is_enabled(index)){
            Button button=new Button(context);
            button.setBackgroundColor(android.R.attr.buttonBarButtonStyle);
            button.setText("停用此区块");
            linearLayout.addView(button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    wait_dia.show();
                    new async_shell("swapoff "+gen_dev_path(index),context).start();
                }
            });
            {
                TextView textView=new TextView(context);
                textView.setText("* 这将停用本区块，本区块中的数据将会被转移至内存中（这会花费不少时间）。在停用区块后，您将可以移除这个区块（前提使内核支持）。只要您不移除本区块，本区块将可以在稍后被重新启用。");
                linearLayout.addView(textView);
            }
        } else {
            //注册了但未启用
            {
                Button button = new Button(context);
                button.setBackgroundColor(android.R.attr.buttonBarButtonStyle);
                button.setText("启用此区块");
                linearLayout.addView(button);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        EditText editText = new EditText(context);
                        editText.setHint("32758");

                        new AlertDialog.Builder(context)
                                .setTitle("设置区块优先级")
                                .setMessage("你可以为区块配置一个优先级，优先级的取值范围是0-32767。优先级更高的区块相比于其它区块更容易被使用到。")
                                .setView(editText)
                                .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        if (editText.getText().toString().equals(""))
                                            editText.setText("32758");
                                        wait_dia.show();
                                        new async_shell("swapon " + gen_dev_path(index) + " -p " + editText.getText().toString(), context).start();
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .create().show();
                    }
                });
                {
                    TextView textView = new TextView(context);
                    textView.setText("* 这将启用本区块，使内存中的数据可以被转移至本区块并压缩。");
                    linearLayout.addView(textView);
                }
            }
        }
        else{
            //区块未注册
            Button button=new Button(context);
            button.setBackgroundColor(android.R.attr.buttonBarButtonStyle);
            button.setText("注册区块");
            linearLayout.addView(button);
            long finalDisksize = disksize;
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(finalDisksize <= 0){
                        Toast.makeText(context,"只有在区块大小大于0时才能注册区块！",Toast.LENGTH_LONG).show();
                        return;
                    }
                    wait_dia.show();
                    new async_shell("mkswap "+gen_dev_path(index),context).start();
                }
            });
            {
                TextView textView=new TextView(context);
                textView.setText("* 注册区块将会把这个区块加载为ZRAM，但是，它并不会被启用。只有在启用这个区块后，它才会被使用。");
                linearLayout.addView(textView);
            }
        }
        //注册了但未启用，或者未注册未启用但是disksize大于0
        if(is_registered(index)&&!is_enabled(index)
                ||!is_registered(index)&&!is_enabled(index)&&disksize>0){
            Button button = new Button(context);
            button.setBackgroundColor(android.R.attr.buttonBarButtonStyle);
            linearLayout.addView(button);
            if(is_registered(index)) {
                button.setText("反注册此区块");
                {
                    TextView textView = new TextView(context);
                    textView.setText("* 这将会取消此区块的注册，在取消注册后，您将可以调整区块的大小与压缩算法。");
                    linearLayout.addView(textView);
                }
            }else {
                button.setText("重置此区块");
                {
                    TextView textView = new TextView(context);
                    textView.setText("* 这将会重置此区块，重置后，您将可以调整区块的大小与压缩算法。");
                    linearLayout.addView(textView);
                }
            }
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    wait_dia.show();
                    new async_shell("echo 1 > "+gen_conf_path(index)+"/reset", context).start();
                }
            });
        }

        if(!is_enabled(index)&&support_zram_control()){
            Button button=new Button(context);
            button.setBackgroundColor(android.R.attr.buttonBarButtonStyle);
            button.setText("移除区块");
            linearLayout.addView(button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    wait_dia.show();
                    new async_shell("echo 1 > "+ZRAM_CONTROL_PATH+"/reset\n" +
                            "echo "+index+ " > "+ZRAM_CONTROL_PATH+"/hot_remove",context).start();
                }
            });
            {
                TextView textView=new TextView(context);
                textView.setText("* 移除区块后，这个ZRAM区块将会从列表中消失");
                linearLayout.addView(textView);
            }
        }

        {
            TextView textView=new TextView(context);
            textView.setText("* 开机应用保存当前已启用的区块信息，其余区块将会被忽略。");
            linearLayout.addView(textView);
        }

        dia= new AlertDialog.Builder(context)
                .setTitle("管理ZRAM区块"+index)
                .setView(scrollView)
                .create();
        dia.show();

    }

    class async_shell extends Thread{
        String cmd;
        Context context;
        public async_shell(String cmd,Context context){
            this.cmd=cmd;
            this.context=context;
        }
        public void run(){
            ShellUtil.run(cmd,true);
            //防止新建页面的闪退
            try {
                wait_dia.dismiss();
                dia.dismiss();
            }catch (Exception e){ }
            ((Activity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    genInsideView(context);
                }
            });
        }
    }

    boolean is_registered(int index){
        for(int i:registered_blocks){
            if(index==i)
                return true;
        }
        return false;
    }

    boolean is_enabled(int index){
        for(int i:enabled_blocks){
            if(index==i)
                return true;
        }
        return false;
    }

    List<String> gen_registered_zram_blocks(){
        return StringToList.to(ShellUtil.run("for zram in $(blkid | grep swap | "+busybox_awk+"awk -F[/:] '{print $4}')\n" +
                "do\n" +
                "echo ${zram} | grep -o \"[0-9]*$\"\n" +
                "done\n",true));
    }

    /*
    有时候这屑东西会变成这个鬼样子
    OnePlus8:/ # cat /proc/swaps
    Filename                                Type            Size    Used    Priority
    /dev/block/zram0                        partition       2150396 0       -2
    OnePlus8:/ # cat /proc/swaps
    Filename                                Type            Size    Used    Priority
    /block/zram0                            partition       2150396 8864    -2
    dev会消失草
    至于这是为什么，我暂且蒙在鼓里
    那就handle一下吧
    OnePlus8:/ # cat /proc/swaps | grep zram | awk -F[/" "] '{print $3 $4}'
    blockzram0
    因为截取的是最后的数字
    所以不会对结果产生影响
     */
    List<String> gen_enabled_zram_blocks(){
        return StringToList.to(ShellUtil.run("for zram in $(cat /proc/swaps | grep zram | "+busybox_awk+"awk -F[/\" \"] '{print $3 $4}')\n" +
                "do\n" +
                "echo ${zram} | grep -o \"[0-9]*$\"\n" +
                "done\n",true));
    }

    List<String> gen_all_zram_blocks(){
        return StringToList.to(ShellUtil.run("for zram in `ls /dev/block | grep zram`\n" +
                "do\n" +
                "echo ${zram} | grep -o \"[0-9]*$\"\n" +
                "done\n",true));
    }

    boolean support_mm_stat(int index){
        return ShellUtil.run("if [ -e "+gen_conf_path(index)+"/mm_stat ]\n" +
                "then\n" +
                "echo true\n" +
                "fi\n",true).equals("true");
    }

    boolean incompatible(){
        if(ShellUtil.run("if [ ! -e \""+ZRAM_CONTROL_PATH+"\" -a ! -e \""+gen_conf_path(0)+"\" ]\n" +
                "then\n" +
                "echo fuck\n" +
                "fi",true).equals("fuck")){
            return true;
        }
        return check_sys_awk();
    }

    boolean check_sys_awk(){
        String str=ShellUtil.run("if [ -e /bin/awk ]\n" +
                "then\n" +
                "echo 1\n" +
                "elif [ -e /data/adb/magisk/busybox ]\n" +
                "then\n" +
                "echo 2\n" +
                "fi\n",true);

        if (str.equals("2"))
            busybox_awk="/data/adb/magisk/busybox ";
        else
            return !str.equals("1");

        return false;
    }

    boolean support_zram_control(){
        return ShellUtil.run("if [ -e "+ZRAM_CONTROL_PATH+" ]\n" +
                "then\n" +
                "echo true\n" +
                "fi\n",true).equals("true");
    }

    String gen_conf_path(int index){
        return ZRAM_CONF_PATH+index;
    }

    String gen_dev_path(int index){
        return ZRAM_DEV_PATH+index;
    }

    @Override
    public String onBootApply() {
        if(incompatible())
            return null;

        ArrayList<String> size=new ArrayList<>();
        ArrayList<String> algorithm=new ArrayList<>();
        String cmd="";

        for(int index:enabled_blocks){
            //获取并保存每个已经激活的区块的尺寸与压缩算法
            //未激活的全部略过，懒得搞了
            size.add(ShellUtil.run("cat "+ZRAM_CONF_PATH+index+"/disksize",true));
            algorithm.add(ShellUtil.run("cat "+ZRAM_CONF_PATH+index+"/comp_algorithm",true));
        }

        //干掉所有已有的zram区块
        cmd+="for zram in $(blkid | grep swap | "+busybox_awk+"awk -F[/:] '{print $4}')\n" +
                "do\n" +
                "index=\"$(echo $zram | grep -o \"[0-9]*$\")\"\n" +
                "swapoff "+ZRAM_DEV_PATH+"${index}\n" +
                "echo 1 > "+ZRAM_CONF_PATH+"${index}/reset\n" +
                "echo ${index} > "+ZRAM_CONTROL_PATH+"/hot_remove\n" +
                "done\n";

        //按照保存的内容创建新的区块
        //注意兼容3.18内核
        for(int index=0;index<size.size();index++){
            //3.18下此循环应该只会执行一次
            cmd+="cat "+ZRAM_CONTROL_PATH+"/hot_add\n";
            //先写入算法，再写入尺寸
            cmd+=write_cmd(ZRAM_CONF_PATH+index+"/comp_algorithm",algorithm.get(index))+"\n";
            cmd+=write_cmd(ZRAM_CONF_PATH+index+"/disksize",size.get(index))+"\n";
            //注册这个区块
            cmd+="mkswap "+ZRAM_DEV_PATH+index+"\n";
            cmd+="swapon "+ZRAM_DEV_PATH+index+"\n";
        }

        //保存通用设置
        cmd+=write_cmd(VM_PATH+"/swappiness",ShellUtil.run("cat "+VM_PATH+"/swappiness",true))+"\n";


        return cmd;
    }

    @Override
    public void onExit() {

    }
}
