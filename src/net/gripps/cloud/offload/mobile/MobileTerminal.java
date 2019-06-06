package net.gripps.cloud.offload.mobile;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.CloudCPU;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VM;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.StartTimeComparator;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.environment.CPU;
import net.gripps.environment.Machine;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * 携帯端末を表す．
 * 既存のMachineクラスに対し，処理能力や消費電力，信号の受信強度などが追加される．
 * Created by kanem on 2018/11/07.
 */
public class MobileTerminal extends Machine  implements Serializable, Cloneable{
    /**
     * 実際の無線の信号強度を考慮した帯域幅
     * BWから計算される．
     * "Efficient Multi-User Computation Offloading for Mobile-Edge Cloud Computing"の(30)の式
     * から導出される．
     */
    private double rBW;

    /**
     * rBWの計算に必要な，チャネルの重み値．全員1だと，均等，という意味．
     */
    private double channelWeight;

    /**
     * 今，使っているチャネル番号
     */
    private int channelNo;

    /**
     * 信号の送信電力，もしくは受信電力．
     */
    private double trPower;

    /**
     * チャネル利得（当該端末?基地局間の）
     * 距離，周波数のそれぞれの2乗に比例して小さくなる．
     */
    private double gain;

    /**
     * バックグラウンドノイズの電力
     */
    private double backNoise;

    /**
     * SFCタイプのアプリケーション．この場合は，一つのみを保持しているものとする．
     */
    private SFC apl;

    /**
     * Worklowをすべて端末内で処理した場合の電力消費量
     */
    private double totalInitialEnergyConsumption;

    /**
     * 現時点での，処理に要する電力消費量
     */
    private double currentEnergyConsumption;

    private double tau;

    /**
     * Offloadありで必要な電力 / ローカル実行の電力
     */
    private double energyRate;


    /**
     * 割り当てられたVNFのキュー
     */
    private PriorityQueue<VNF> vnfQueue;

    /**
     * すべてのタスクをローカル実行したときの時間
     */
    private double localExecTime;

    /**
     *
     * @param machineID
     * @param cpuMap
     * @param num
     * @param rBW
     * @param channelWeight
     * @param channelNo
     * @param trPower
     * @param gain
     * @param backNoise
     * @param apl
     */
    public MobileTerminal(long machineID, TreeMap<Long, CPU>
            cpuMap, int num, double rBW, long bw, double channelWeight, int channelNo, double trPower, double gain, double backNoise, SFC apl) {
        super(machineID, cpuMap, num);
        this.rBW = rBW;
        this.channelWeight = channelWeight;
        this.channelNo = channelNo;
        this.trPower = trPower;
        this.gain = gain;
        this.backNoise = backNoise;
        this.apl = apl;
        this.setBw(bw);
        this.totalInitialEnergyConsumption = 0;
        this.tau = CloudUtil.genDouble2(CloudUtil.mobile_device_tau_min, CloudUtil.mobile_device_tau_max,
                CloudUtil.dist_mobile_device_tau, CloudUtil.dist_mobile_device_tau_mu);
        this.vnfQueue = new PriorityQueue<VNF>(5, new StartTimeComparator());
        this.energyRate = 0.0d;
        this.localExecTime = 0.0d;


    }

    public double getLocalExecTime() {
        return localExecTime;
    }

    public void setLocalExecTime(double localExecTime) {
        this.localExecTime = localExecTime;
    }

    public SFC getApl() {
        return apl;
    }

    public void setApl(SFC apl) {
        this.apl = apl;
    }

    public long  getSpeed(){
        TreeMap<Long, CPU> cpuMap = this.getCpuMap();
        CloudCPU cpu = (CloudCPU)cpuMap.get(new Long(0));
        return cpu.getMips();
    }

    public double getEnergyRate() {
        return energyRate;
    }

    public void setEnergyRate(double energyRate) {
        this.energyRate = energyRate;
    }

    /**
     * この端末によって使用されているチャネルのリスト
     */
   // private LinkedList<ComChannel> usedChannel;




    public double getrBW() {
        return rBW;
    }

    public void setrBW(double rBW) {
        this.rBW = rBW;
    }

    public double getTrPower() {
        return trPower;
    }

    public void setTrPower(double trPower) {
        this.trPower = trPower;
    }

    public double getGain() {
        return gain;
    }

    public void setGain(double gain) {
        this.gain = gain;
    }

    public double getBackNoise() {
        return backNoise;
    }

    public void setBackNoise(double backNoise) {
        this.backNoise = backNoise;
    }

    public int getChannelNo() {
        return channelNo;
    }

    public void setChannelNo(int channelNo) {
        this.channelNo = channelNo;
    }

    public double getChannelWeight() {
        return channelWeight;
    }

    public void setChannelWeight(double channelWeight) {
        this.channelWeight = channelWeight;
    }

    public double getTotalInitialEnergyConsumption() {
        return totalInitialEnergyConsumption;
    }

    public void setTotalInitialEnergyConsumption(double totalInitialEnergyConsumption) {
        this.totalInitialEnergyConsumption = totalInitialEnergyConsumption;
    }

    public double getCurrentEnergyConsumption() {
        return currentEnergyConsumption;
    }

    public void setCurrentEnergyConsumption(double currentEnergyConsumption) {
        this.currentEnergyConsumption = currentEnergyConsumption;
    }

    public double getTau() {
        return tau;
    }

    public void setTau(double tau) {
        this.tau = tau;
    }

    public PriorityQueue<VNF> getVnfQueue() {
        return vnfQueue;
    }

    public void setVnfQueue(PriorityQueue<VNF> vnfQueue) {
        this.vnfQueue = vnfQueue;
    }
}
