package net.gripps.cloud.offload.core;

import net.gripps.cloud.CloudUtil;
import net.gripps.cloud.core.CloudCPU;
import net.gripps.cloud.core.Core;
import net.gripps.cloud.core.VCPU;
import net.gripps.cloud.nfv.NFVUtil;
import net.gripps.cloud.offload.mobile.MobileTerminal;
import net.gripps.clustering.common.aplmodel.BBTask;
import net.gripps.mapping.ClusterMappingManager;

import java.util.HashMap;
import java.util.Vector;

/**
 * Offloadを管理するためのクラスです．
 * シングルトンオブジェクトになります．
 * Created by Hidehiro Kanemitsu on 2018/11/08.
 */
public class OffloadManager{

    public static OffloadManager own;

    private OffloadManager(){

    }

    public static OffloadManager getIns(){
        if(OffloadManager.own == null){
            OffloadManager.own  = new OffloadManager();

        }else{

        }
        return OffloadManager.own;
    }


}
