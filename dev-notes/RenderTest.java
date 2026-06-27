import java.util.*;

public class RenderTest {
    static int width = 33;
    static double fov = 180;
    static int interval = 30;
    static char filler = '-';
    static char pointer = '^';

    record Mark(int bearing, String label) {}

    static List<Mark> marks() {
        Map<Integer,Mark> m = new LinkedHashMap<>();
        for (int d=0; d<360; d+=interval) m.put(d, new Mark(d, Integer.toString(d)));
        m.put(0,new Mark(0,"N")); m.put(90,new Mark(90,"E"));
        m.put(180,new Mark(180,"S")); m.put(270,new Mark(270,"W"));
        return new ArrayList<>(m.values());
    }

    static double yawToBearing(float yaw){ double b=(yaw+180.0)%360.0; return b<0?b+360:b; }
    static double delta(double to,double from){ double d=(to-from)%360; if(d<-180)d+=360; if(d>=180)d-=360; return d; }

    static String render(float yaw){
        double bearing=yawToBearing(yaw);
        int center=width/2; double dpc=fov/width; double half=fov/2;
        char[] buf=new char[width]; Arrays.fill(buf,filler);
        for(Mark mk:marks()){
            double dl=delta(mk.bearing(),bearing);
            if(Math.abs(dl)>half+3*dpc) continue;
            int cc=(int)Math.round(center+dl/dpc);
            String lab=mk.label(); int start=cc-lab.length()/2;
            for(int i=0;i<lab.length();i++){int idx=start+i; if(idx<0||idx>=width)continue; buf[idx]=lab.charAt(i);}
        }
        if(buf[center]==filler) buf[center]=pointer;
        return "["+new String(buf)+"]";
    }

    public static void main(String[] a){
        // yaw 0=South, 90=West, 180=North, 270/-90=East
        float[][] cases={{0,'S'},{90,'W'},{180,'N'},{-90,'E'},{-45,0},{135,0}};
        String[] names={"facing SOUTH (yaw 0)","facing WEST (yaw 90)","facing NORTH (yaw 180)",
                        "facing EAST (yaw -90)","facing SE (yaw -45)","facing NW (yaw 135)"};
        for(int i=0;i<cases.length;i++){
            System.out.printf("%-22s bearing=%3.0f  %s%n", names[i], yawToBearing(cases[i][0]), render(cases[i][0]));
        }
    }
}
