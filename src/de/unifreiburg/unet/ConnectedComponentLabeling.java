/**************************************************************************
 *
 * Copyright (C) 2018 Thorsten Falk
 *
 *        Image Analysis Lab, University of Freiburg, Germany
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 **************************************************************************/

package de.unifreiburg.unet;

import ij.plugin.PlugIn;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.IJ;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.process.ByteProcessor;

import java.util.Arrays;
import java.util.Vector;
import java.util.List;
import java.util.LinkedList;

import java.awt.Point;

public class ConnectedComponentLabeling implements PlugIn {

  public static final int SIMPLE_NEIGHBORHOOD = 0;
  public static final int COMPLEX_NEIGHBORHOOD = 1;

  private static class TreeNode<K,V> {

    private K _key;
    private V _value;

    private TreeNode<K,V> _parent = null;
    private final List< TreeNode<K,V> > _children =
        new LinkedList< TreeNode<K,V> >();

    private TreeNode<K,V> _root = null;

    public TreeNode(K key) {
            _key = key;
            _value = null;
            _root = this;
          }

    public TreeNode(K key, V value) {
            _key = key;
            _value = _value;
            _root = this;
          }

    public K key() {
      return _key;
    }

    public void setKey(K key) {
      _key = key;
    }

    public V value() {
      return _value;
    }

    public void setValue(V value) {
      _value = value;
    }

    public TreeNode<K,V> parent() {
      return _parent;
    }

    public List< TreeNode<K,V> > children() {
            return _children;
          }

    public TreeNode<K,V> root() {
      return _root;
    }

    public void reparent(TreeNode<K,V> parent) {
      if (_parent == parent) return;
      if (_parent != null) _parent._removeChild(this);
      _parent = parent;
      if (parent != null) parent._addChild(this);
      _updateRoot();
    }

    public void addChild(TreeNode<K,V> child) {
      child.reparent(this);
    }

    public void removeChild(TreeNode<K,V> child) {
      child.reparent(null);
    }

    private void _removeChild(TreeNode<K,V> child) {
      _children.remove(child);
    }

    private void _addChild(TreeNode<K,V> child) {
      _children.add(child);
    }

    private void _updateRoot() {
      if (_parent == null) _root = this;
      else _root = _parent._root;
      for (TreeNode<K,V> node: _children) node._updateRoot();
    }
  }

/*======================================================================*/
/*!
 *   2/3-D connected component labeling.
 *
 *   \param imp    The ImagePlus to compute the connected components of.
 *                 The connected component labeling is computed for each time
 *                 frame and channel independently. Value interpretation:
 *                 0 = background, all other values are treated as foreground.
 *   \param nhood  The neighborhood to use, one of
 *                 SIMPLE_NEIGHBORHOOD (4- or 6-connected) or
 *                 COMPLEX_NEIGHBORHOOD (8- or 26-connected)
 *
 *   \return 1. The number of connected components per channel and frame where
 *           the number of connected components in channel c of frame t is
 *           stored at array index t * nChannels + c.
 *           2. an integer Blob containing the labeled connected component
 *           masks. The label for pixel (t,c,z,y,x) is stored at position
 *           ((t * nChannels + c) * nSlices + z) * height + y) * width + x.
 */
/*======================================================================*/
  public static Pair< Integer[],Blob<Integer> >
  label(ImagePlus imp, int nhood, ProgressMonitor pr) {

    int T = imp.getNFrames();
    int C = imp.getNChannels();
    int D = imp.getNSlices();
    int W = imp.getWidth();
    int H = imp.getHeight();

    if (pr.getMax() == 0) pr.init(0, "", "", 2 * T * C * D);

    // Prepare upper left half of neighborhood (rest is not needed)
    int[] dx = null, dy = null, dz = null;
    if (D == 1)
    {
      switch (nhood)
      {
      case COMPLEX_NEIGHBORHOOD: // == 8-connected
        dx = new int[] { -1,  0,  1, -1 };
        dy = new int[] { -1, -1, -1,  0 };
        dz = new int[] {  0,  0,  0,  0 };
        break;
      default: // is SIMPLE_NEIGHBORHOOD == 4-connected
        dx = new int[] {  0, -1 };
        dy = new int[] { -1,  0 };
        dz = new int[] {  0,  0 };
      }
    }
    else
    {
      switch (nhood)
      {
      case COMPLEX_NEIGHBORHOOD: // == 26-connected
        dx = new int[] { -1,  0,  1, -1,  0,  1, -1,  0,  1, -1,  0,  1, -1 };
        dy = new int[] { -1, -1, -1,  0,  0,  0,  1,  1,  1, -1, -1, -1,  0 };
        dz = new int[] { -1, -1, -1, -1, -1, -1, -1, -1, -1,  0,  0,  0,  0 };
        break;
      default: // is SIMPLE_NEIGHBORHOOD == 6-connected
        dx = new int[] {  0,  0, -1 };
        dy = new int[] {  0, -1,  0 };
        dz = new int[] { -1,  0,  0 };
      }
    }

    Calibration cal = imp.getCalibration();
    double factor = 1.0;
    switch (cal.getUnit())
    {
    case "m":
    case "meter":
      factor = 1000000.0;
      break;
    case "cm":
    case "centimeter":
      factor = 10000.0;
      break;
    case "mm":
    case "millimeter":
      factor = 1000.0;
      break;
    case "nm":
    case "nanometer":
      factor = 0.0001;
      break;
    case "pm":
    case "pikometer":
      factor = 0.0000001;
      break;
    }

    int[] shape = null;
    double[] elSize = null;
    if (D == 1) {
      shape = new int[] { T, C, H, W };
      elSize = new double[] {
          factor * cal.pixelHeight, factor * cal.pixelWidth };
    }
    else {
      shape = new int[] { T, C, D, H, W };
      elSize = new double[] {
          factor * cal.pixelDepth, factor * cal.pixelHeight,
          factor * cal.pixelWidth };
    }
    Blob<Integer> labelBlob = new Blob<Integer>(shape, elSize, Integer[].class);
    Integer[] labels = (Integer[])labelBlob.data();
    Arrays.fill(labels, 0);

    Integer[] nComps = new Integer[T * C];

    int outIdx = 0;
    for (int t = 0; t < T; ++t)
    {
      for (int c = 0; c < C; ++c)
      {
        int stackStart = outIdx;

        int nextLabel = 1;
        Vector< TreeNode<Integer,Object> > lbl =
            new Vector< TreeNode<Integer,Object> >();
        // Add a null entry for label 0, so that the array can be directly
        // indexed by the instance labels
        lbl.add(null);

        for (int z = 0; z < D; ++z) {
          pr.count(1);
          ImageProcessor ip = null;
          if (imp.getStackSize() == 1) ip = imp.getProcessor();
          else ip = imp.getStack().getProcessor(
              imp.getStackIndex(c + 1, z + 1, t + 1));
          for (int y = 0; y < H; ++y) {
            for (int x = 0; x < W; ++x, ++outIdx) {
              if (ip.getf(x, y) == 0) continue;
              int val = labels[outIdx];
              for (int nbIdx = 0; nbIdx < dx.length; ++nbIdx) {
                if (x + dx[nbIdx] < 0 || x + dx[nbIdx] >= W ||
                    y + dy[nbIdx] < 0 || y + dy[nbIdx] >= H ||
                    z + dz[nbIdx] < 0 || z + dz[nbIdx] >= D) continue;
                int nbVal = labels[
                    outIdx + (dz[nbIdx] * H + dy[nbIdx]) * W + dx[nbIdx]];
                if (nbVal == 0 || val == nbVal) continue;
                if (val == 0)
                {
                  labels[outIdx] = nbVal;
                  val = nbVal;
                  continue;
                }
                if (lbl.get(val).root().key() < lbl.get(nbVal).root().key())
                    lbl.get(nbVal).root().reparent(lbl.get(val).root());
                else if (lbl.get(val).root().key() >
                         lbl.get(nbVal).root().key())
                    lbl.get(val).root().reparent(lbl.get(nbVal).root());
              }
              if (val == 0) {
                labels[outIdx] = nextLabel;
                lbl.add(new TreeNode<Integer,Object>(nextLabel++));
              }
            }
          }
        }

        // Generate dense label mapping
        int[] labelMap = new int[lbl.size()];
        Arrays.fill(labelMap, 0);
        int currentLabel = 1;
        for (int i = 1; i < lbl.size(); ++i)
        {
          if (labelMap[lbl.get(i).root().key()] == 0)
              labelMap[lbl.get(i).root().key()] = currentLabel++;
          labelMap[i] = labelMap[lbl.get(i).root().key()];
        }
        nComps[t * C + c] = currentLabel - 1;

        // Re-map preliminary labels to final labels
        int i = 0;
        for (int z = 0; z < D; ++z) {
          pr.count(1);
          for (int j = 0; j < W * H; ++j, ++i) {
            labels[stackStart + i] = labelMap[labels[stackStart + i]];
          }
        }
      }
    }
    pr.end();
    return new Pair< Integer[],Blob<Integer> >(nComps, labelBlob);
  }

  @Override
  public void run(String arg) {
    ImagePlus imp = IJ.getImage();
    if (imp == null) IJ.noImage();
    try
    {
      ProgressMonitor pr = new ProgressMonitor(null);
      pr.initNewTask("Connected component labeling", 1.0f, 0);
      label(imp, COMPLEX_NEIGHBORHOOD, pr).second.convertToImagePlus().show();
    }
    catch(BlobException e)
    {
      IJ.error(e.toString());
    }
  }

}
