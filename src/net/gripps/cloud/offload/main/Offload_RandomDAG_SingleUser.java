package net.gripps.cloud.offload.main;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.ComputeHost;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.nfv.sfc.SFC;
import net.gripps.cloud.nfv.sfc.SFCGenerator;
import net.gripps.cloud.nfv.sfc.VNF;
import net.gripps.cloud.offload.core.MCCEnvironment;
import net.gripps.cloud.offload.mobile.MobileTerminal;
import net.gripps.cloud.offload.scheduling.PCTSO_Algorithm;
import net.gripps.clustering.common.aplmodel.DataDependence;

import java.util.Iterator;

/**
 * Created by kanem on 2018/11/07.
 */
public class Offload_RandomDAG_SingleUser {

    /**
     * DAGを生成して，モバイルorクラウドで処理を行うというモデル．
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            //設定ファイルを取得
            String fileName = args[0];
            //Utilの初期化（設定ファイルの値の読み込み）
            NFVUtil.getIns().initialize(fileName);

            //SFCの生成
            //VNF集合の生成
           SFC sfc = SFCGenerator.getIns().multipleSFCProcess();
            //SFC sfc = SFCGenerator.getIns().singleSFCProcess();
            //SFC sfc2 = (SFC)sfc.deepCopy();

            //次はクラウド環境の生成
            //設定値の読み込みを行う．
            CloudUtil.getInstance().initialize(fileName);
            //MCC (Mobile Cloud Computing)の環境設定
            MCCEnvironment env = new MCCEnvironment();
            //MCCEnvironment env2 = (MCCEnvironment)env.deepCopy();


            Iterator<VNF> vIte = sfc.getVnfMap().values().iterator();
            long totalSize = 0;
            while(vIte.hasNext()){
                VNF vnf = vIte.next();
                totalSize += vnf.getWorkLoad();
            }

            Iterator<VNF> vnfIte = sfc.getVnfMap().values().iterator();
            long totalWorkload = 0;
            long totalDataSize = 0;
            long totalEdgeNum = 0;
            while(vnfIte.hasNext()){
                VNF vnf = vnfIte.next();
                totalWorkload += vnf.getWorkLoad();
                totalEdgeNum += vnf.getDsucList().size();
                Iterator<DataDependence> dsucIte = vnf.getDsucList().iterator();
                while(dsucIte.hasNext()) {
                    DataDependence dd = dsucIte.next();
                    totalDataSize += dd.getMaxDataSize();
                }
            }
            //次に，環境．
            long totalSpeed = 0;
            long totalBW = 0;
            long hostNum = env.getGlobal_hostMap().size();
            Iterator<ComputeHost> cIte = env.getGlobal_hostMap().values().iterator();
            while(cIte.hasNext()){
                ComputeHost host = cIte.next();
                totalBW += host.getBw();
            }
            double ave_bw = NFVUtil.getRoundedValue((double)totalBW / (double)hostNum);

            Iterator<VCPU> vcpuIte = env.getGlobal_vcpuMap().values().iterator();
            long vcpuNum = env.getGlobal_vcpuMap().size();
            while(vcpuIte.hasNext()){
                VCPU vcpu = vcpuIte.next();
                totalSpeed += vcpu.getMips();
            }
            System.out.println("HostNum:"+env.getGlobal_hostMap().size() + "/vCPUNum:"+env.getGlobal_vcpuMap().size());

            double ave_speed = NFVUtil.getRoundedValue((double)totalSpeed/(double)vcpuNum);

            double ave_workload = NFVUtil.getRoundedValue((double)totalWorkload / (double) sfc.getVnfMap().size());
            double ave_datasize = NFVUtil.getRoundedValue((double)totalDataSize / (double)totalEdgeNum);

            double ave_comTime = NFVUtil.getRoundedValue((double)ave_datasize / (double) ave_bw);
            double ave_procTime = NFVUtil.getRoundedValue((double)ave_workload / (double)ave_speed);
            double CCR = NFVUtil.getRoundedValue((double)ave_comTime / (double)ave_procTime);

            System.out.println("CCR: "+ CCR + " /VNF Num:"+sfc.getVnfMap().size());


            PCTSO_Algorithm pctso = new PCTSO_Algorithm(env, sfc);
            //各モバイル端末のローカル処理時間の最大値を求める．
            Iterator<MobileTerminal> mIte0  = env.getMobileMap().values().iterator();
            double maxLocalExecTime = 0;
            while(mIte0.hasNext()){
                MobileTerminal m = mIte0.next();
                double val = m.getLocalExecTime();
                if(val >= maxLocalExecTime){
                    maxLocalExecTime = val;
                }

            }
            SFC sfc2 =(SFC)sfc.deepCopy();
            MCCEnvironment env2 = (MCCEnvironment)env.deepCopy();


            //pctso.setContinuous(true);
            pctso.mainProcess();
            Iterator<MobileTerminal> mIte = env.getMobileMap().values().iterator();
            long totalLocalTaskNum = 0;
            double totalenergyRate = 0;

            while(mIte.hasNext()){
                MobileTerminal m = mIte.next();
                totalLocalTaskNum += m.getVnfQueue().size();
                totalenergyRate += CloudUtil.getRoundedValue((double)100*m.getCurrentEnergyConsumption() / (double)m.getTotalInitialEnergyConsumption());

            }
            double localRate =CloudUtil.getRoundedValue( (double)100*totalLocalTaskNum / (double)sfc.getVnfMap().size());
            double aveEnergyRate = CloudUtil.getRoundedValue((double)totalenergyRate / (double)env.getMobileMap().size());
            double rate1 = pctso.calcTotalActualEnergy();
            System.out.println("SpeedUp[PCTSO]:"+NFVUtil.getRoundedValue(maxLocalExecTime/pctso.getMakeSpan()/*pctso.getTotalCPProcTimeAtMaxSpeed()*/) +"/ AveEnergyRate:"+rate1 +
                    " / LocalRate:"+localRate+"% / # of vCPUs: "+pctso.getAssignedVCPUMap().size()+ "/ # of Hosts:"+pctso.getHostSet().size()
                    /*+ "/# of Ins:"+pctso.calcTotalFunctionInstanceNum()*/);
            //次に，平均のオフロード率を調べる．

/*
            MCCEnvironment env2 = (MCCEnvironment)pctso.getEnv().deepCopy();
            SFC sfc2 = (SFC)pctso.getSfc().deepCopy();
*/
            PCTSO_Algorithm pctso2 = new PCTSO_Algorithm(env2, sfc2);
            pctso2.setContinuous(true);
            pctso2.mainProcess();

            Iterator<MobileTerminal> mIte2 = env2.getMobileMap().values().iterator();
            long totalLocalTaskNum2 = 0;
            double totalenergyRate2 = 0;
            while(mIte2.hasNext()){
                MobileTerminal m = mIte2.next();
                totalLocalTaskNum2 += m.getVnfQueue().size();
                totalenergyRate2 += CloudUtil.getRoundedValue((double)100*m.getCurrentEnergyConsumption() / (double)m.getTotalInitialEnergyConsumption());

            }
            double localRate2 =CloudUtil.getRoundedValue( (double)100*totalLocalTaskNum2 / (double)sfc.getVnfMap().size());
            double aveEnergyRate2 = CloudUtil.getRoundedValue((double)totalenergyRate2 / (double)env2.getMobileMap().size());
            double rate2= pctso2.calcTotalActualEnergy();
            System.out.println("SpeedUp[PCTSO2]:"+NFVUtil.getRoundedValue(maxLocalExecTime/pctso2.getMakeSpan()/*pctso2.getTotalCPProcTimeAtMaxSpeed()*/) +"/ AveEnergyRate:"+rate2 +
                            " / LocalRate:"+localRate2+"% / # of vCPUs: "+pctso2.getAssignedVCPUMap().size()+ "/ # of Hosts:"+pctso2.getHostSet().size()
                    /*+ "/# of Ins:"+pctso.calcTotalFunctionInstanceNum()*/);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
