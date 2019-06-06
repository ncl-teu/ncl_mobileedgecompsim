package net.gripps.cloud.offload.scheduling;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.CloudEnvironment;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.cloud.offload.mobile.MobileTerminal;
import net.gripps.clustering.common.aplmodel.CustomIDSet;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Priority-based Continuous Task Selection for Offloading(PCTSO)
 * アルゴリズムの実装クラスです．
 *
 * Created by kanem on 2018/11/07.
 */
public class BAK_PCTSO_Algorithm extends BaseOffloadTaskSchedulingAlgorithm {

    protected LinkedBlockingQueue<VNF> pivotFreeSet;


    public BAK_PCTSO_Algorithm(CloudEnvironment env, SFC sfc) {
        super(env, sfc);
        this.pivotFreeSet = new LinkedBlockingQueue<VNF>();
    }


    /**
     * taskの後続タスクへのデータについて，最大値を取得する．
     * @param task
     * @return
     */
    public double calcMaxComTime(VNF task){
        Iterator<DataDependence> dsucIte = task.getDsucList().iterator();
        double retComTime = 0.0d;
        MobileTerminal m = this.env.getMobileMap().get(task.getMachineID());

        while(dsucIte.hasNext()){
            DataDependence dsuc = dsucIte.next();
            double tmpVal = CloudUtil.getRoundedValue((double)dsuc.getMaxDataSize()/(double)Math.min(this.usedBW, m.getrBW()));
            if(tmpVal >= retComTime){
                retComTime = tmpVal;
            }

        }
        return retComTime;
    }

    public  double calcDeltaExecTime(MobileTerminal m){
        double ret = 0.0d;
        long total = 0;
        Iterator<VNF> pivotFreeIte = this.pivotFreeSet.iterator();
        while(pivotFreeIte.hasNext()){
            VNF vnf = pivotFreeIte.next();
            total += vnf.getWorkLoad();
        }
        double val = CloudUtil.getRoundedValue(total/this.usedSpeed - total/m.getSpeed());

        return val;

    }

    /**
     * メインとなるメソッドです．
     */
    public void mainProcess(){

        while(!this.freeVNFSet.isEmpty()){
            this.pivotFreeSet.clear();
            //まずは，pivotタスクを選択する．
            VNF pivot = this.selectPivot();

            //pivotのoffloadを試みる．これにより
            // T_f(pivot@cloud) +Max{ T_c(pivot->mobile)} <= T_f(pivot@mobile)
            //であり，[かつ トータルの電力消費量 <= オリジナル電力消費量]であればoffloadする(x)
            //これを満たさない（mobileで実行）場合は，モバイルで処理する(B)
            //(A)の場合，pivotの後続タスクでblevelを支配するものを取得する．
            //それがfreeであれば，条件(x)の判断をする．

            this.pivotFreeSet.offer(pivot);
            while(pivot != null){
                //pivotをクラウドへ移動した場合の，データ到着時刻の最悪値を計算する．
                MobileTerminal m = this.env.getMobileMap().get(pivot.getMachineID());
                double drt = this.calcEDRTAtCloud(pivot, m, new VCPU());
                //pivotをモバイルで処理した場合の完了時刻を計算する．
                double ft = this.calcEFTAtMobile(pivot, m);
                if(drt < ft){
                    //オフロードする．
                    //オフロード時の電力変動は，
                    // -τ*仕事量 + pivot -> (m_kへの送信データサイズ * p_k)の合計（出辺の分）+ m内のタスク->pivotへの送信電力の合計
                    //現状の差分を取得する．
                    double deltaEnergy = m.getTotalInitialEnergyConsumption() - m.getCurrentEnergyConsumption();
                    //pivotからの後続タスクへのデータ送信（データ受信）にかかる電力量の合計
                    Iterator<DataDependence> dsucIte = pivot.getDsucList().iterator();
                    double totalRecEnergy = 0;
                    while(dsucIte.hasNext()){
                        DataDependence dsuc = dsucIte.next();
                        totalRecEnergy += dsuc.getMaxDataSize() * m.getTrPower();

                    }
                    Iterator<DataDependence> dpredIte = pivot.getDpredList().iterator();
                    double totalSendEnergy = 0;
                    while(dpredIte.hasNext()){
                        DataDependence dpred = dpredIte.next();
                        //先行タスクを取得する．
                        VNF predVNF = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
                        //先行タスクがクラウドであれば，電力は発生しない．
                        if(predVNF.getvCPUID() == null){
                            //モバイルに割り当てられているので，電力発生
                            totalSendEnergy += dpred.getMaxDataSize() * m.getTrPower();
                        }
                    }
                    //pivotをオフロードすることによる電力の差分を取得する．
                    double difEnergy = (-1) * m.getTau() * pivot.getWorkLoad() /*+totalRecEnergy*/ + totalSendEnergy;
                    if(difEnergy <= deltaEnergy){
                        //たとえ増えていたとしても，initial - 現在の差分以下（つまり，トータルとしてマイナス）であれば
                        //オフロードは受け入れられる．
                        //スケジュールする．
                        this.scheduleVNF(pivot, this.vcpuMap);
                    }else{
                        //クラウドにオフロードすると，初期電力を超えてしまう場合
                        //モバイル側でスケジュール
                        this.scheduleVNFAtMobile(pivot, m);

                    }
                    m.setCurrentEnergyConsumption(m.getCurrentEnergyConsumption()+difEnergy);

                }else{
                    //モバイルのほうが早い場合はそのまま
                    this.scheduleVNFAtMobile(pivot, m);
                    Iterator<DataDependence> dpredIte = pivot.getDpredList().iterator();
                    double totalSendEnergy = 0;
                    while(dpredIte.hasNext()){
                        DataDependence dpred = dpredIte.next();
                        //先行タスクを取得する．
                        VNF predVNF = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
                        //先行タスクがクラウドであれば，電力は発生しない．
                        if(predVNF.getvCPUID() == null){
                            //モバイルに割り当てられているので，電力発生
                            totalSendEnergy += dpred.getMaxDataSize() * m.getTrPower();
                        }
                    }
                    m.setCurrentEnergyConsumption(m.getCurrentEnergyConsumption()+totalSendEnergy);
                }



            }


            //次に，pivotを起点としたs_freeを決める．
            //pivotを起点として，そのblevelを支配している後続タスク集合を取得する．
            CustomIDSet nextSet = this.createPivotCandidate(pivot);
            //Sに何らかのものが入っている間のループ
            while(!nextSet.isEmpty()){
                //nextSetから，SL(S_free)の差分が最も小さくなるもの（かつマイナス）
                //となる後続タスクを決める．
                Iterator<Long> nextIte = nextSet.iterator();
                while(nextIte.hasNext()){
                    Long id = nextIte.next();
                    VNF offloadedTask = this.sfc.findVNFByLastID(id);
                    //offloadedTaskの中から，データ送信時間の最大値を取得する．
                    double maxComTime = this.calcMaxComTime(offloadedTask);
                    MobileTerminal m = this.env.getMobileMap().get(offloadedTask.getMachineID());
                    //S_free内での処理時間の差分を計算．
                    double deltaExecTime = this.calcDeltaExecTime(m);
                    Iterator<VCPU> vITe = this.env.getGlobal_vcpuMap().values().iterator();
                    //start時刻の差分計算のためのループ
                    double minStartTime = CloudUtil.MAXValue;
                    VCPU targetVCPU = null;
                    while(vITe.hasNext()){
                        VCPU vcpu = vITe.next();
                        //最後に，offloadedTaskの開始時刻の差分を計算する．
                        double est = this.calcEST(offloadedTask, vcpu);
                        if(est <= minStartTime){
                            minStartTime = est;
                            targetVCPU = vcpu;
                        }
                    }
                    //offloadedTaskをもしオフロードしない場合（mで処理する場合）
                    //の開始時刻を求める．
                    //先行タスクからの到着時刻を求める．
                }
            }
            System.out.println("test");

        }
    }

    public double calcDRT(VNF task, MobileTerminal m){
        Iterator<DataDependence> dpredIte = task.getDpredList().iterator();
        while(dpredIte.hasNext()){
            DataDependence dpred = dpredIte.next();


        }
        return 0;
    }
    public boolean isGoToFreeNext(VNF task){
        Iterator<DataDependence> dpredIte = task.getDpredList().iterator();
        int cnt = 0;
        while(dpredIte.hasNext()){
            DataDependence dpred = dpredIte.next();
            if(dpred.getIsChecked()){
                continue;
            }else{
                cnt++;
            }
        }
        if(cnt == 1){
            return true;
        }else{
            return false;
        }

    }
    /**
     * pivotの後続タスクのうち，pivotがスケジュール済みだとfreeとなるものの集合を作ります．
     * @param pivot
     * @return
     */
    public CustomIDSet createPivotCandidate(VNF pivot){
        CustomIDSet retSet = new CustomIDSet();
        Iterator<DataDependence> dsucIte = pivot.getDsucList().iterator();
        while(dsucIte.hasNext()){
            DataDependence dsuc = dsucIte.next();
            //後続タスクを取得する．
            VNF sucTask = this.sfc.findVNFByLastID(dsuc.getToID().get(1));
            //未チェックな入力辺が一つのみかどうかチェック．
            if (this.isGoToFreeNext(sucTask)) {
                //Sへ入れる．
                retSet.add(sucTask.getIDVector().get(1));


            }

        }
        return retSet;

    }

    public VNF selectPivot(){
        //Freeリストについて，levelが最大のものを選択する．
        Iterator<Long> freeIte = this.freeVNFSet.iterator();
        VNF pivot = null;
        double maxLevel = 0.0d;
        while(freeIte.hasNext()){
            Long id = freeIte.next();
            //タスクを取得する．
            VNF task = this.sfc.findVNFByLastID(id);
            double level = this.calcLevel(task);
            if(maxLevel <= level){
                maxLevel = level;
                pivot = task;
            }
        }
        return pivot;
    }


    public double calcLevel(VNF task){
        //指定タスクのレベルを取得する．
        //まずは，当該タスクの開始時刻を見る．
        //先行タスクの終了時刻を見て，そこからデータ到着時刻を計算する．
        double ret_startTime = 0.0d;
        Iterator<DataDependence> dpredIte = task.getDpredList().iterator();
        MobileTerminal m = this.env.getMobileMap().get(task.getMachineID());
        while(dpredIte.hasNext()){
            DataDependence dpred = dpredIte.next();
            VNF predTask = this.sfc.findVNFByLastID(dpred.getFromID().get(1));
            //start時刻を求める．
            double tmp_arrivalTime = predTask.getFinishTime() + this.calcComTimeInAllCase(predTask, task, dpred.getMaxDataSize());
            if(tmp_arrivalTime >= ret_startTime){
                ret_startTime = tmp_arrivalTime;
            }
        }
        //double retVal = ret_startTime + this.calcExecTime(task.getWorkLoad(), m) + this.calcRLevel(task);
        //Level = 開始時刻 + blevel
        double retVal = ret_startTime + task.getBlevel();
        return retVal;
    }




    /**
     * vnf以外のタスクで，UEX+Freeリストに入っているタスクのトータル実行時間を求めます．
     * つまり，UEX - vnfの時間になります．
     *
     * @param task
     * @return
     */
    public double calcRLevel(VNF task){
        //UEXInfoを取得
        UEXInfo info = this.uexMap.get(task.getMachineID());
        MobileTerminal m = this.env.getMobileMap().get(info.getmID());

        long currentUEXSize = info.getUEXSize();
        currentUEXSize -= task.getWorkLoad();
        //info.setUEXSize(currentUEXSize);

        return CloudUtil.getRoundedValue((double)currentUEXSize/(double)m.getSpeed());
    }

    public LinkedBlockingQueue<VNF> getPivotFreeSet() {
        return pivotFreeSet;
    }

    public void setPivotFreeSet(LinkedBlockingQueue<VNF> pivotFreeSet) {
        this.pivotFreeSet = pivotFreeSet;
    }
}
