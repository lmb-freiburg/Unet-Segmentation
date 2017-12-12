import com.jcraft.jsch.*;

public class MySftpProgressMonitor implements SftpProgressMonitor {

  private UnetJob _job = null;
  private long _reportedCount = 0;
  private long _count = 0;
  private long _max = 0;
  private boolean _canceled = false;

  public MySftpProgressMonitor(UnetJob job) {
    _job = job;
  }

  public void init(int op, String src, String dest, long max) {
    _max = max;
    _count = 0;
    if (_job != null) _job.setTaskProgress(_count, _max);
  }

  public boolean count(long count) {
    _count += count;
    if((int)(100 * (float)_reportedCount / (float)_max) >=
       (int)(100 * (float)_count / (float)_max)) return true;
    _reportedCount = _count;
    if (_job != null) {
      _job.setTaskProgress(_count, _max);
      _job.setProgress(
          (int) (_job.getTaskProgressMin() + (float) _count / (float) _max *
                 (_job.getTaskProgressMax() - _job.getTaskProgressMin())));
      _canceled = _job.interrupted();
      return !_canceled;
    }
    return true;
  }

  public void end() {
    if (_job != null) {
      _job.setTaskProgress(_max, _max);
      _job.setProgress((int) _job.getTaskProgressMax());
    }
  }

  public boolean canceled() {
    return _canceled;
  }

};
