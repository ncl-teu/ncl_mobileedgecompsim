package net.gripps.cloud.offload.scheduling;

import net.gripps.cloud.nfv.sfc.SFC;

import java.io.Serializable;

/**
 * Created by Hidehiro Kanemitsu on 2019/01/04.
 */
public class UEXInfo implements Serializable {

    protected SFC sfc;

    protected long UEXSize;

    protected Long mID;

    public UEXInfo(SFC sfc, long UEXSize, Long mID) {
        this.sfc = sfc;
        this.UEXSize = UEXSize;
        this.mID = mID;
    }

    public SFC getSfc() {
        return sfc;
    }

    public void setSfc(SFC sfc) {
        this.sfc = sfc;
    }

    public long getUEXSize() {
        return UEXSize;
    }

    public void setUEXSize(long UEXSize) {
        this.UEXSize = UEXSize;
    }

    public Long getmID() {
        return mID;
    }

    public void setmID(Long mID) {
        this.mID = mID;
    }
}
