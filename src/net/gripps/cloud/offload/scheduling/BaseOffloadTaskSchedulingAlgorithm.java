package net.gripps.cloud.offload.scheduling;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.*;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.*;
import net.gripps.cloud.offload.core.MCCEnvironment;
import net.gripps.cloud.offload.mobile.MobileTerminal;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.*;

/**
 * Created by Hidehiro Kanemitsu on 2018/12/31.
 */
public class BaseOffloadTaskSchedulingAlgorithm extends BaseVNFSchedulingAlgorithm {

    protected MCCEnvironment env;

    protected HashMap<Long, UEXInfo> uexMap;

    public BaseOffloadTaskSchedulingAlgorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.uexMap = new HashMap<Long, UEXInfo>();

        if(env instanceof  MCCEnvironment){
            this.env = (MCCEnvironment)env;
        }else{
            System.out.println("Please use MCCEnvironment!");
            System.exit(-1);
        }
        //次に，各SFCを各端末へ割り当てる．
        //Iterator<SFC> sfcIte = SFCGenerator.getIns().getSfcList().iterator();
        Iterator<MobileTerminal> mIte = this.env.getMobileMap().values().iterator();
        LinkedList<SFC> sfcList = SFCGenerator.getIns().getSfcList();
        int len = sfcList.size();
        int idx = 0;
        while(mIte.hasNext()){
            MobileTerminal m = mIte.next();
            SFC s = sfcList.get(idx);
            long totalW = 0;
            m.setApl(s);
            this.configureInitialEnergy(m, s);
            UEXInfo info = new UEXInfo(s, this.calcSFCSize(s, m.getMachineID()), m.getMachineID());
            this.uexMap.put(m.getMachineID(), info);
            //SFC内の各VNFに，割当先をセットする．
            Iterator<VNF> vIte = s.getVnfMap().values().iterator();

            while(vIte.hasNext()){
                VNF v = vIte.next();
                v.setMachineID(m.getMachineID());
                totalW += v.getWorkLoad();
            }
            double execTime = CloudUtil.getRoundedValue(totalW / (double)m.getSpeed());
            m.setLocalExecTime(execTime);
            idx++;
        }

        Iterator<Long> startIte = this.sfc.getStartVNFSet().iterator();
        //テキトーなidを割り振る．
        Iterator<Long> idIte = this.env.getMobileMap().keySet().iterator();
        Long mID = idIte.next();

        while(startIte.hasNext()){
            Long startID = startIte.next();
            VNF startTask = this.sfc.findVNFByLastID(startID);
            if(startTask.getMachineID() == null){
                startTask.setMachineID(mID);
            }
        }
        Iterator<Long> endIte = this.sfc.getEndVNFSet().iterator();
        while(endIte.hasNext()){
            Long endID = endIte.next();
            VNF endTask = this.sfc.findVNFByLastID(endID);
            if(endTask.getMachineID() == null){
                endTask.setMachineID(mID);
            }
        }

    }



    /**
     * トータルとしての実際の電力消費量を求めます．
     * @return
     */
    public double calcTotalActualEnergy(){
        Iterator<MobileTerminal> mIte = this.env.getMobileMap().values().iterator();
        double val = 0.0d;
        double totalRate = 0.0d;
        //携帯端末ごとのループ
        while(mIte.hasNext()){
            MobileTerminal m = mIte.next();
            double mVal = 0.0d;
            SFC sfc = m.getApl();
            Iterator<VNF> vIte = sfc.getVnfMap().values().iterator();
            while(vIte.hasNext()){
                VNF v = vIte.next();
                Iterator<DataDependence> dsucIte = v.getDsucList().iterator();
                //クラウドなら，処理にかかる電力はなし
                if(v.getvCPUID() != null){
                    //データを見る．
                    while(dsucIte.hasNext()){
                        DataDependence dsuc = dsucIte.next();
                        VNF sucVNF = this.sfc.findVNFByLastID(dsuc.getToID().get(1));
                        //もし後続タスクがモバイル側であれば電力あり
                        if(sucVNF.getvCPUID() == null){
                            mVal += dsuc.getMaxDataSize() * m.getTrPower();
                        }else{
                            //後続タスクもクラウドなら，何もしない．
                        }
                    }
                }else{
                    //モバイル側
                    mVal += m.getTau() * v.getWorkLoad();
                    while(dsucIte.hasNext()){
                        DataDependence dsuc = dsucIte.next();
                        VNF sucVNF = this.sfc.findVNFByLastID(dsuc.getToID().get(1));
                        //もし後続タスクがモバイル側であれば何もしない．
                        if(sucVNF.getvCPUID() == null){
                            //mVal += dsuc.getMaxDataSize() * m.getTrPower();
                        }else{
                            //クラウド側であれば電力あり．
                            mVal += dsuc.getMaxDataSize() * m.getTrPower();
                        }
                    }
                }
            }
            double rate = CloudUtil.getRoundedValue((double)mVal /(double)m.getTotalInitialEnergyConsumption());
            m.setEnergyRate(rate);
            val += mVal;
            totalRate += rate;
        }
        double ret = CloudUtil.getRoundedValue((double)totalRate /(double)this.env.getMobileMap().size() );
        return ret;
    }

    public boolean addVNFQueue(MobileTerminal m, VNF vnf){
        PriorityQueue<VNF> queue = m.getVnfQueue();
        Iterator<VNF> vITe = queue.iterator();
        boolean isFound = false;
        while(vITe.hasNext()){
            VNF v = vITe.next();
            if(v.getIDVector().get(1).longValue() == vnf.getIDVector().get(1).longValue()){
                isFound = true;
                break;
            }
        }
        if(isFound){
            return false;
        }else{
            queue.add(vnf);
        }
        return true;
    }



    public void scheduleVNFAtMobile(VNF vnf, MobileTerminal m){
        double ret_finishtime = NFVUtil.MAXValue;
        double ret_starttime = NFVUtil.MAXValue;
        this.freeVNFSet.remove(vnf.getIDVector().get(1));

        ret_finishtime = this.calcEFTAtMobile(vnf,m);

        //vnfの時刻を更新する．
        ret_starttime = ret_finishtime - this.calcExecTime(vnf.getWorkLoad(), m);
        vnf.setStartTime(ret_starttime);
        vnf.setFinishTime(ret_finishtime);
        vnf.setEST(ret_starttime);
        vnf.setMachineID(m.getMachineID());
        vnf.setvCPUID(null);

        //retCPUにおいて，vnfを追加する
        // retCPU.getVnfQueue().add(vnf);
        this.addVNFQueue(m, vnf);

       // double ct = this.calcCT(retCPU);

        //未スケジュール集合から削除する．
        this.unScheduledVNFSet.remove(vnf.getIDVector().get(1));

        //Freeリスト更新
        this.updateFreeList(vnf);




    }

    /**
     * 当該vnfの後続タスクのうち，freeリストに入っているもののみの
     * tlevel値を更新する処理です．
     * @param vnf
     * @param m
     */
    public void updateTlevel(VNF vnf, MobileTerminal m) {
        Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
        while (dsucIte.hasNext()) {
            DataDependence dsuc = dsucIte.next();
            VNF sucVNF = this.sfc.findVNFByLastID(dsuc.getToID().get(1));
            if (this.freeVNFSet.contains(sucVNF.getIDVector().get(1))) {
                //freeに入っているときだけ，考慮する．
                this.configureVNFTlevel(sucVNF,m);
            } else {
                continue;
            }
        }

    }

    public void configureVNFTlevel(VNF vnf, MobileTerminal m) {
        Iterator<DataDependence> dpredIte = vnf.getDpredList().iterator();
        double maxTlevel = -1d;
        VCPU toVCPU = this.env.getGlobal_vcpuMap().get(vnf.getvCPUID());

        while (dpredIte.hasNext()) {
            DataDependence dpred = dpredIte.next();
            VNF fromVNF = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
            double tmpValue = 0;
            //もし先行タスクがクラウドであれば
            if(fromVNF.getvCPUID() != null){
                //vcpuに割り当てられている場合
                VCPU fromVCPU = this.env.getGlobal_vcpuMap().get(fromVNF.getvCPUID());
                tmpValue = fromVNF.getFinishTime() + this.calcComTime(dpred.getMaxDataSize(), fromVCPU, m);
            }else{
                //先行タスクがモバイルであれば
                tmpValue = fromVNF.getFinishTime();
            }

            if (maxTlevel <= tmpValue) {
                maxTlevel = tmpValue;
                vnf.setDominantPredID(fromVNF.getIDVector().get(1));
                vnf.setTlevel(maxTlevel);
            }
        }
    }

    /**
     * vnfをオフロードしなかった場合（モバイル側で実行）の
     * vnfの完了時刻を計算します.
     *
     *
     * @param vnf
     * @param m
     * @return
     */
    public double calcEFTAtMobile(VNF vnf, MobileTerminal m){
        Iterator<DataDependence> dpredIte = vnf.getDpredList().iterator();
        double startTime = 0;

        while(dpredIte.hasNext()){
            DataDependence dpred = dpredIte.next();

            //先行タスク取得
            VNF predVNF = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
            //先行タスクがモバイル側で処理されている場合
            if(predVNF.getvCPUID() == null){
                startTime = Math.max(startTime, predVNF.getFinishTime());
            }else{
                VCPU predVCPU = this.vcpuMap.get(predVNF.getvCPUID());
                //先行タスクがクラウド側である場合は，DRTを考慮する．
                double drt = predVNF.getFinishTime() + this.calcComTime(dpred.getMaxDataSize(), predVCPU, m);
                startTime = Math.max(startTime, drt);
            }
        }
        //startTime ~ finishTimeの間に，すでにスケジュール済みのものがあるかどうか
        Iterator<VNF> vIte = m.getVnfQueue().iterator();
        double maxStartTime = 0;
        while(vIte.hasNext()){
            VNF v = vIte.next();
            //OKな場合
            if((v.getFinishTime() <= startTime)||(v.getStartTime() >= startTime + this.calcExecTime(vnf.getWorkLoad(), m))){
                continue;
            }else{
                //重なっている場合は，ENDテクニックを行う．
                maxStartTime = Math.max(maxStartTime, v.getFinishTime());
            }
        }
        double retStartTime = 0;
        //挿入がOKなら
        if(maxStartTime != 0){
            retStartTime = startTime;
        }else{
            retStartTime = maxStartTime;
        }
        double retFinishTime = retStartTime + this.calcExecTime(vnf.getWorkLoad(), m);
        return retFinishTime;

    }
    /**
     * vnfをクラウド側へオフロードした場合の，データ到着時刻を算出します．
     * EDRT (Earliest Data Arrival Time)
     * @param vnf
     * @param m
     * @param retVCPU
     * @return
     */
    public double calcEDRTAtCloud(VNF vnf, MobileTerminal m, VCPU retVCPU){
        Iterator<VCPU> vIte = this.vcpuMap.values().iterator();
        double retFtime = CloudUtil.MAXValue;
        if(retVCPU == null){
            retVCPU = new VCPU();
        }


        while(vIte.hasNext()){
            VCPU vcpu = vIte.next();
            double est = this.calcEST(vnf, vcpu);
            //プログラム送信時間
//            double offloadTime = this.calcComTime(CloudUtil.offload_program_datasize, m, vcpu);

            //完了時刻
            double finishTime = est + this.calcExecTime(vnf.getWorkLoad(), vcpu);
            //通信時間
            Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
            double maxComTime = 0;
            while(dsucIte.hasNext()){
                DataDependence dsuc = dsucIte.next();
                double comTime = this.calcComTime(dsuc.getMaxDataSize(), vcpu, m);
             /*   if(comTime >= 1000){
                    System.out.println("af");
                }
                */
                if(comTime >= maxComTime){
                    maxComTime = comTime;
                }
            }
            double totalTime = finishTime + maxComTime;

            if(totalTime <= retFtime){
                retFtime = totalTime;
                retVCPU = vcpu;
            }

        }
        return retFtime;

    }

    /**
     * 指定の端末において，割り当てられているsfcをローカル時刻した
     * ときの電力消費量を求める．
     * @param m
     * @param sfc
     */
    public void configureInitialEnergy(MobileTerminal m, SFC sfc){
        Iterator<VNF> vIte = sfc.getVnfMap().values().iterator();
       double totalVal = 0;
        while(vIte.hasNext()){
            VNF vnf = vIte.next();
            double val = m.getTau() * vnf.getWorkLoad();
            totalVal += val;

        }
        m.setTotalInitialEnergyConsumption(totalVal);
         /*
        m.setCurrentEnergyConsumption(totalVal);
        */
    }

    public double calcComTimeInAllCase(VNF fromVNF, VNF toVNF, long dataSize){
        //クラウド側にあるのなら，vcpuを見る．
        if(fromVNF.isAssignedInCloud()){
            VCPU fromVCPU = this.env.getGlobal_vcpuMap().get(fromVNF.getvCPUID());
            MobileTerminal toM = this.env.getMobileMap().get(toVNF.getMachineID());
            return this.calcComTime(dataSize, fromVCPU, toM);
        }else{
            //クラウドでない，つまりモバイル端末内であれば，通信時間は無し．
            return 0;
        }
    }

    /**
     * 未スケジュールタスクの合計サイズを求めて，さらに各タスクの割当先をモバイル端末に設定します．
     * @param sfc
     * @param mID
     * @return
     */
    public long calcSFCSize(SFC sfc, Long mID){
        long val = 0L;
        Iterator<VNF> vnfIte = sfc.getVnfMap().values().iterator();
        while(vnfIte.hasNext()){
            VNF vnf = vnfIte.next();
            vnf.setMachineID(mID);
            vnf.setAssignedInCloud(false);
            val += vnf.getWorkLoad();

        }
        return val;
    }

    /**
     * 指定vnfをcpuへ割り当てたときの最早開始時刻を求める．
     * @param vnf
     * @param cpu
     * @return
     */
    @Override
    protected double calcEST(VNF vnf, VCPU cpu) {
        double  arrival_time = 0;

        if (vnf.getDpredList().isEmpty()) {

        } else {
            Iterator<DataDependence> dpredIte = vnf.getDpredList().iterator();
            while (dpredIte.hasNext()) {
                DataDependence dpred = dpredIte.next();
                VNF dpredTask = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
                double tmp_arrival_time = 0;
                if(dpredTask.isAssignedInCloud()){
                    //クラウドへ割り当てられれば，通常計算．
                    //先行VNFのvcpuを取得する．
                    VCPU predCPU = this.findVCPU(dpredTask.getvCPUID());

                    double  nw_time = 0;
                    //先行タスクからのデータ転送時間を求める．
                    nw_time = this.calcComTime(dpred.getMaxDataSize(), predCPU, cpu);

                    tmp_arrival_time = dpredTask.getStartTime() + this.calcExecTime(dpredTask.getWorkLoad(), predCPU) + nw_time;
                }else{
                    //モバイル側であれば，モバイル側からのデータ到着時刻を求める．
                    //先行VNFのvcpuを取得する．
                    //VCPU predCPU = this.findVCPU(dpredTask.getvCPUID());
                    MobileTerminal  m = this.env.getMobileMap().get(dpredTask.getMachineID());

                    double  nw_time = 0;
                    //先行タスクからのデータ転送時間を求める．
                    //携帯 -> クラウドでの通信時間
                    nw_time = this.calcComTime(dpred.getMaxDataSize(), m, cpu);
                    tmp_arrival_time = dpredTask.getStartTime() + this.calcExecTime(dpredTask.getWorkLoad(), m) + nw_time;
                }

                if (arrival_time <= tmp_arrival_time) {
                    arrival_time = tmp_arrival_time;
                }
            }
        }
        //arrival_time(DRT) ~ 最後のFinishTimeまでの範囲で，task/cpu速度の時間が埋められる
        //箇所があるかどうかを調べる．
        Object[] oa = cpu.getVnfQueue().toArray();
        double ret_starttime = NFVUtil.MAXValue;

        if(oa.length > 1){
            boolean isInserted = false;
            //startTimeの小さい順にソート
            Arrays.sort(oa, new StartTimeComparator());
            int len = oa.length;
            for (int i = 0; i < len - 1; i++) {
                VNF t = ((VNF) oa[i]);
                double  finish_time = t.getStartTime() + this.calcExecTime(t.getWorkLoad(), cpu);
                //次の要素の開始時刻を取得する．
                VNF t2 = ((VNF) oa[i + 1]);
                double start_time2 = t2.getStartTime();
                double  s_candidateTime = Math.max(finish_time, arrival_time);
                //当該タスクの終了時刻を計算する．
                double ftime = s_candidateTime + this.calcExecTime(vnf.getWorkLoad(), cpu);
                //挿入可能な場合は，その候補の開始時刻を返す．
                if (ftime <= start_time2) {
                    //s_candidateTime ~ fTimeの間の，利用率合計値の最大値を計算する．
                    if(this.constrainedMode == 1){
                        Core core = this.env.getGlobal_coreMap().get(cpu.getCorePrefix());
                        if(this.isAssignedInDuration(s_candidateTime, ftime, core, cpu, vnf)){
                            //割り当て可能なら，計算続行
                            if(ret_starttime >= s_candidateTime){
                                ret_starttime = s_candidateTime;
                                isInserted = true;
                            }
                        }else{
                            //過負荷のために割り当て不能なら，continueする．
                            continue;
                        }
                    }

                } else {
                    continue;
                }

            }
            if(isInserted){
                return ret_starttime;
            }else{
                //挿入できない場合は，ENDテクニックを行う．
                //ENDテクニックであれば，過負荷とはならない．
                VNF finTask = ((VNF) oa[len - 1]);
                double end_starttime = Math.max(finTask.getStartTime() + this.calcExecTime(finTask.getWorkLoad(), cpu), arrival_time);
                double end_finishtime = end_starttime + this.calcExecTime(vnf.getWorkLoad(), cpu);
                Core c = this.env.getGlobal_coreMap().get(cpu.getCorePrefix());
                if(this.constrainedMode == 1) {
                    if(this.isAssignedInDuration(end_starttime, end_finishtime, c, cpu, vnf)){
                        return end_starttime;
                    }else{
                        //ENDテクニックでもだめなら，もう片方のVCPUが終わるまで．
                        Iterator<VCPU> vITe = c.getvCPUMap().values().iterator();
                        double ret_newstarttime = -1;
                        while(vITe.hasNext()){
                            VCPU v = vITe.next();
                            double CT = this.calcCT(v);
                            if(CT >= ret_newstarttime){
                                ret_newstarttime = CT;
                            }

                        }
                        return ret_newstarttime;
                    }
                }else{
                    return Math.max(finTask.getStartTime() + this.calcExecTime(finTask.getWorkLoad(), cpu), arrival_time);

                }
            }

        }else{

            double currentST = this.calcST(cpu);
            double currentCT = this.calcCT(cpu);
            Core core = this.env.getGlobal_coreMap().get(cpu.getCorePrefix());

            //bakfillできる場合，
            double assumedCT = arrival_time + this.calcExecTime(vnf.getWorkLoad(), cpu);
            boolean flg = false;
            if(assumedCT <= currentST){
                if(this.constrainedMode == 1) {
                    //bakfillする．
                    if(this.isAssignedInDuration(arrival_time, assumedCT, core, cpu, vnf)){
                        return arrival_time;
                    }else{
                        //currentCTと他vcpuのCTの大きい方を，開始時刻とする．
                        flg = true;
                    }
                }else{
                    return arrival_time;
                }

            }else{
                //currentCTと他vcpuのCTの大きい方を，開始時刻とする．
                flg = true;
            }
            if(flg){
                if(this.constrainedMode == 1) {
                    Iterator<VCPU> vITe = core.getvCPUMap().values().iterator();
                    double ret_newstarttime = -1;
                    while(vITe.hasNext()){
                        VCPU v = vITe.next();
                        double CT = this.calcCT(v);
                        if(CT >= ret_newstarttime){
                            ret_newstarttime = CT;
                        }

                    }
                    return ret_newstarttime;

                }else{
                    return currentCT;
                }
            }

        }
        return arrival_time;


    }

    /**
     *
     * @param dataSize
     * @param m
     * @param toCPU
     * @return
     */
    public double calcComTime(long dataSize, MobileTerminal m, VCPU toCPU) {
        //宛先となるvCPUが属するDCのIDを取得する．
        Long toDCID = CloudUtil.getInstance().getDCID(toCPU.getPrefix());
        Cloud toCloud = env.getDcMap().get(toDCID);
        Long toHostID = CloudUtil.getInstance().getHostID(toCPU.getPrefix());
        //宛先ホストを取得する．
        ComputeHost toHost = toCloud.getComputeHostMap().get(toHostID);
        //クラウド側の最小のBWをもとめる．
        long bw_cloud = Math.min(toCloud.getBw(), toHost.getBw());

        //後は，モバイル端末との最小値となるBWを求める．
        double realBW = Math.min((double)bw_cloud, m.getrBW());

        double retTime = CloudUtil.getRoundedValue((double)dataSize/realBW);

        return retTime;
    }

    /**
     *
     * @param dataSize
     * @param fromCPU データ送信元@クラウド
     * @param m データ送信先@モバイル端末
     * @return
     */
    public double calcComTime(long dataSize, VCPU fromCPU, MobileTerminal m) {
        //宛先となるvCPUが属するDCのIDを取得する．
        Long fromDCID = CloudUtil.getInstance().getDCID(fromCPU.getPrefix());
        Cloud fromCloud = env.getDcMap().get(fromDCID);
        Long fromHostID = CloudUtil.getInstance().getHostID(fromCPU.getPrefix());
        //宛先ホストを取得する．
        ComputeHost fromHost = fromCloud.getComputeHostMap().get(fromHostID);
        //クラウド側の最小のBWをもとめる．
        long bw_cloud = Math.min(fromCloud.getBw(), fromHost.getBw());

        // 後は，モバイル端末との最小値となるBWを求める．
        double realBW = Math.min((double)bw_cloud, m.getrBW());

        double retTime = CloudUtil.getRoundedValue((double)dataSize/realBW);

        return retTime;
    }

    /**
     * 携帯電話上で処理した場合の実行時間を計算します．
     * @param workLoad
     * @param m
     * @return
     */
    public double calcExecTime(long workLoad, MobileTerminal m) {
        CloudCPU cpu = (CloudCPU)m.getCpuMap().get(new Long(0));
        double val = CloudUtil.getRoundedValue((double)workLoad/(double)cpu.getMips());

        return val;
    }

    @Override
    public MCCEnvironment getEnv() {
        return env;
    }

    public void setEnv(MCCEnvironment env) {
        this.env = env;
    }

    public HashMap<Long, UEXInfo> getUexMap() {
        return uexMap;
    }

    public void setUexMap(HashMap<Long, UEXInfo> uexMap) {
        this.uexMap = uexMap;
    }
}
