package de.uni_koblenz.west.cidre.common.executor;

import java.util.Comparator;

public class WorkerTaskComparator implements Comparator<WorkerTask> {

	@Override
	public int compare(WorkerTask o1, WorkerTask o2) {
		long diff = o1.getEstimatedTaskLoad() - o2.getEstimatedTaskLoad();
		if (diff < 0) {
			return -1;
		} else if (diff > 0) {
			return 1;
		} else {
			return o1.getID() - o2.getID();
		}
	}

}
