package net.gripps.cloud.offload.core;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.*;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.cloud.offload.mobile.MobileTerminal;
import net.gripps.environment.CPU;
import net.gripps.environment.Environment;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;

/**
 * MCC (Mobile Cloud Computing)の環境クラスです．
 * モバイル端末の集合と，クラウドの環境を定義します．
 * 最初は各モバイル端末にDAGが割り当てられていて，
 * そして一部をクラウドへ移す，ということを行う．
 *
 *
 * Created by Hidehiro Kanemitsu  on 2018/11/08.
 */
public class MCCEnvironment extends CloudEnvironment implements Serializable, Cloneable{

    /**
     * モバイル端末のマップ
     */
    protected HashMap<Long, MobileTerminal> mobileMap;

    protected HashMap<String, Core> global_CoreMapMobile;

    /**
     *
     */
    public MCCEnvironment() {
        super();
        this.mobileMap = new HashMap<Long, MobileTerminal>();

        this.global_CoreMapMobile = new HashMap<String, Core>();

        //モバイル端末のセットアップを行う．
        this.setupMobileTerminals();

    }

    public MobileTerminal findMobileByCore(Core c){
        MobileTerminal t = this.mobileMap.get(CloudUtil.getInstance().getDCID(c.getPrefix()));

        return t;
    }


    /**
     *
     * @return
     */
    public HashMap<Long, MobileTerminal>  setupMobileTerminals(){
        //まずは，モバイル端末のセットアップを行う．
        //指定数分だけ，モバイル端末を生成して，パラメータも与えておく．

        //チャネル数を取得．
        long channel_num = CloudUtil.mec_channel_num;

        HashMap<Long, Double> cnlMap = new HashMap<Long, Double>();
        for(int idx=0;idx<channel_num;idx++){
            cnlMap.put(new Long(idx), new Double(0));
        }

        //各モバイル端末に対するループ
        for(int i=1;i<=CloudUtil.mobile_device_num;i++){
            long bw = CloudUtil.genLong(CloudUtil.mobile_device_bw_min, CloudUtil.mobile_device_bw_max);
            //現在使用中のチャネル番号をきめる
            long currentChannelNo = channel_num % i;
            double currentSum = cnlMap.get(currentChannelNo);
            //cnlMap.put(currentChannelNo, currentNum+1);

            double power = CloudUtil.genDouble2(CloudUtil.mobile_device_power_min, CloudUtil.mobile_device_power_max,
                    CloudUtil.dist_mobile_device_power, CloudUtil.dist_mobile_device_power_mu);

            double gain = CloudUtil.genDouble2(CloudUtil.mobile_device_gain_min, CloudUtil.mobile_device_gain_max,
                    CloudUtil.dist_mobile_device_gain, CloudUtil.dist_mobile_device_gain_mu);
            cnlMap.put(currentChannelNo,currentSum + power*gain);


            //コア数
            int coreNum = CloudUtil.genInt(CloudUtil.mobile_device_core_num_min, CloudUtil.mobile_device_core_num_max);
            long mips = CloudUtil.genLong(CloudUtil.mobile_device_cpu_mips_min, CloudUtil.mobile_device_cpu_mips_min);
            HashMap<Long, Core> coreMap = new HashMap<Long, Core>();

            //コア数分だけのループ
            for(int l=0;l < coreNum;l++){
                //double rate = CloudUtil.genDouble(CloudUtil.core_mips_rate_min, CloudUtil.core_mips_rate_max);
                HashMap<Long, VCPU> vcpuMap = new HashMap<Long, VCPU>();
                String corePrefix = i +CloudUtil.DELIMITER+"0"+ CloudUtil.DELIMITER + l;
                //System.out.println("core:"+corePrefix);
                /*HashMap<String, Long> pMap = new HashMap<String, Long>();
                pMap.put(CloudUtil.ID_DC, new Long(i));
                pMap.put(CloudUtil.ID_HOST, new Long(j));
                pMap.put(CloudUtil.ID_CPU, new Long(k));
                pMap.put(CloudUtil.ID_CORE, new Long(l));
                */
                //コアの利用率上限値を設定する．
                int maxUsage = NFVUtil.core_max_usage;
                Core c = new Core(corePrefix, 1, (long)(mips*1), new Long(l),  vcpuMap, maxUsage);
                //c.setPrefixMap(pMap);
                coreMap.put(c.getCoreID(), c);
                this.global_CoreMapMobile.put(corePrefix, c);


            }


            //String cpuPrefix = i + CloudUtil.DELIMITER + j + CloudUtil.DELIMITER + k;
            String cpuPrefix = i +CloudUtil.DELIMITER+"0";
            HashMap<String, Long> pMap = new HashMap<String, Long>();
     /*      pMap.put(CloudUtil.ID_DC, new Long(i));
            pMap.put(CloudUtil.ID_HOST, new Long(j));
            pMap.put(CloudUtil.ID_CPU, new Long(k));
*/
            CloudCPU cpu = new CloudCPU(new Long(0), mips, new Vector(), new Vector(),
                    mips, coreMap, cpuPrefix, pMap );
            TreeMap<Long, CPU> cpuMap = new TreeMap<Long, CPU>();

            cpuMap.put(new Long(0), cpu);
            //this.global_cpuMap.put(cpuPrefix, cpu);
            MobileTerminal m = new MobileTerminal(new Long(i), cpuMap, 1, -1, bw,1, (int)currentChannelNo,
                    power, gain, CloudUtil.mobile_device_back_noise,  null);
            this.mobileMap.put(m.getMachineID(), m);


        }
        //次に，rBWを設定する
        //
        Iterator<MobileTerminal> mIte = this.mobileMap.values().iterator();
        while(mIte.hasNext()){
            MobileTerminal m = mIte.next();
            //同一チャネルを使っている数を取得
           double currentTotal = cnlMap.get((long)m.getChannelNo());

            double tmpBw = CloudUtil.getRoundedValue(m.getTrPower() * m.getGain() /
                    ( m.getBackNoise()+(currentTotal-m.getTrPower()*m.getGain())));
            double rBW =CloudUtil.getRoundedValue(m.getBw()* CloudUtil.log2(1+tmpBw));

            m.setrBW(rBW);

        }

        return this.mobileMap;

    }


    public HashMap<Long, MobileTerminal> getMobileMap() {
        return mobileMap;
    }

    public void setMobileMap(HashMap<Long, MobileTerminal> mobileMap) {
        this.mobileMap = mobileMap;
    }
}


