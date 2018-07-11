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

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;

import java.util.Arrays;

/**
 * Generic n-D data container with continuous memory layout
 *
 * @author Thorsten Falk
 * @version 1.0
 */
public abstract class Blob {

  public enum InterpolationType {
      NEAREST, LINEAR;
  }

  protected int[] _shape;
  protected double[] _elementSizeUm;
  protected int[] _stride;

/**
 * Construct a new uninitialized n-D blob with given shape.
 *
 * @param shape The shape of the n-D Blob
 * @param elementSizeUm For any spatial dimension this array must contain
 *   the actual element size in micrometers, for 1-D (e_x), for 2-D (e_y, e_x),
 *   for 3-D (e_z, e_y, e_x). The number of spatial dimensions of the blob
 *   will be deduced from the length of this vector!
 */
  public Blob(int[] shape, double[] elementSizeUm) {
    this._shape = Arrays.copyOf(shape, shape.length);
    this._elementSizeUm = Arrays.copyOf(elementSizeUm, elementSizeUm.length);
    this._stride = new int[shape.length];
    this._stride[_shape.length - 1] = 1;
    for (int d = shape.length - 2; d >= 0; --d)
        this._stride[d] = _stride[d + 1] * _shape[d + 1];
  }

/**
 * Get the number of dimensions of the n-D blob
 *
 * @return The number of dimensions
 */
  public int nDims() {
    return _shape.length;
  }

/**
 * Get the number of spatial dimensions of the n-D blob
 *
 * @return The number of spatial dimensions
 */
  public int nSpatialDims() {
    return _elementSizeUm.length;
  }

/**
 * Get the number of elements of the n-D blob
 *
 * @return The number of elements
 */
  public int size() {
    return _stride[0] * _shape[0];
  }

/**
 * Get the shape of the n-D blob.
 *
 * @return A reference to the shape array
 */
  public int[] shape() {
    return _shape;
  }

/**
 * Get the spatial shape of the n-D blob.
 *
 * @return A copy of the spatial axes of the shape
 */
  public int[] spatialShape() {
    int[] out = new int[_elementSizeUm.length];
    for (int d = 0; d < _elementSizeUm.length; ++d)
        out[d] = _shape[_shape.length - _elementSizeUm.length + d];
    return out;
  }

/**
 * Get the element size in micrometers of the n-D blob.
 *
 * @return A reference to the element size array
 */
  public double[] elementSizeUm() {
    return _elementSizeUm;
  }

/**
 * Get the raw data array for direct access.
 *
 * @return A reference to the raw data array
 */
  public abstract Object data();

/**
 * Create an ImagePlus from this blob for Visualization in ImageJ. Supported
 * types are:
 *   - boolean - Converted to 8-Bit ImagePlus with values 0, 255
 *   - byte    - as is
 *   - short   - as is
 *   - int     - Converted to 16-Bit ImagePlus without overflow check!
 *   - float   - as is
 *   - double  - Converted to 32-Bit ImagePlus
 *
 * If the Blob has less than 5 dimensions leading axes will be omitted in
 * order (t, c, z, y, x), e.g. if the Blob has 3 dimensions they will be
 * treated as (z, y, x).
 *
 * @return The ImagePlus
 *
 * @except BlobException is thrown if the Blob has more than 5 dimensions or
 *         the datatype of the blob is not supported.
 */
  public abstract ImagePlus convertToImagePlus() throws BlobException;

  protected String shapeString() {
    String out = "(";
    for (int d = 0; d < _shape.length - 1; ++d)
        out += _shape[d] + ",";
    out += _shape[_shape.length - 1] + ")";
    return out;
  }

}
