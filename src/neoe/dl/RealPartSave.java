package neoe.dl;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import neoe.util.Log;

public class RealPartSave {
	public boolean allFinished;
	long blocks;
	DL2 dl2;
	long filesize;
	String fn;
	String fnps;
	long lastDone;
	List<RealPart> parts;
	long remain;
	long st0;
	long st1;
	public long sum;
	long sum0;

	public RealPartSave(DL2 dl2) {
		this.dl2 = dl2;
		this.st0 = this.st1 = System.currentTimeMillis();
	}

	public void deleteFile() {
		new File(fnps).delete();
	}

	long getDone() {
		long sum = 0;
		synchronized (this) {
			for (RealPart p : parts) {
				sum += p.doneLen;
			}
		}
		return sum;
	}

	void save(String callerName) throws IOException {
		synchronized (this) {
			String tmpf = fnps + "." + U.ts36();
			DataOutputStream out = new DataOutputStream(new FileOutputStream(tmpf));
			out.writeInt(DL2.ps_version);
			out.writeInt(DL2.blockSize);
			out.writeLong(filesize);
			out.writeLong(parts.size());
			long sum = 0;
			for (RealPart p : parts) {
				out.writeLong(p.start);
				out.writeLong(p.totalLen);
				out.writeLong(p.doneLen);
				sum += p.doneLen;
			}
			out.close();
			File f1 = new File(fnps);
			File f2 = new File(tmpf);
			f1.delete();
			f2.renameTo(f1);
			{
				long speed = 0;
				long t1 = System.currentTimeMillis() - st0;
				if (t1 != 0)
					speed = (sum-sum0) * DL2.blockSize / t1;
				Log.log(String.format("parts %d %d/%d (%.1f%%) %,d KB/s by %s", parts.size(), sum, blocks,
						100.0f * sum / blocks, speed, callerName));

			}
		}
	}

	public void add(long pi) {
		synchronized (this) {
			int partIndex = getPartIndex(pi, 0, parts.size() - 1);
			if (partIndex < 0) {
				U.bug();
			}
			RealPart pt = parts.get(partIndex);
			if (pt.start + pt.doneLen == pi) {
				pt.doneLen++;
				if (pt.isDone())
					inc();
			} else {
				RealPart np = new RealPart();
				np.start = pi;
				np.totalLen = pt.totalLen - (pi - pt.start);
				if (np.totalLen <= 0) {
					U.bug();
				}

				np.doneLen = 1;
				pt.totalLen = pi - pt.start;
				if (pt.totalLen <= 0) {
					U.bug();
				}
				if (np.isDone()) {
					inc();
				}
				if (pt.isDone()) {
					inc();
				}
				parts.add(partIndex + 1, np);
			}
		}
	}

	private void inc() {
		sum++;
		if (sum >= blocks) {
			Log.log("FileWriter all Finished");
			allFinished = true;
		}
	}

	/** 二分法查找 */
	private int getPartIndex(long pi, int i, int j) {
		if (i > j)
			return -1;
		if (i == j) {
			return parts.get(i).isIn(pi) ? i : -1;
		}
		if (isIn(i, pi))
			return i;
		if (isIn(j, pi))
			return j;

		if (j - i == 1) {
			return -1;
		}
		int k = (i + j) / 2;
		if (k == i)
			k++;
		if (isIn(k, pi))
			return k;
		{
			RealPart a = parts.get(k);
			if (pi < a.start) {
				return getPartIndex(pi, i + 1, k - 1);
			}
			if (pi >= a.start + a.doneLen) {
				return getPartIndex(pi, k + 1, j - 1);
			}
		}
		return -1;
	}

	private boolean isIn(int i, long pi) {
		return parts.get(i).isIn(pi);
	}

}