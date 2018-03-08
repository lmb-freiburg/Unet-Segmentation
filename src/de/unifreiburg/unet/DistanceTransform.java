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

import ij.process.FloatProcessor;

public class DistanceTransform {

  public static final float BG_VALUE = Float.POSITIVE_INFINITY;

  private static float[] dt(float[] f, int n) {
    float[] d = new float[n];
    int[] v = new int[n];
    float[] z = new float[n + 1];
    int k = 0;
    v[0] = 0;
    z[0] = -BG_VALUE;
    z[1] = BG_VALUE;
    for (int q = 1; q <= n - 1; q++) {
      float s  = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) /
          (2 * q - 2 * v[k]);
      while (s <= z[k]) {
        k--;
        s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
      }
      k++;
      v[k] = q;
      z[k] = s;
      z[k+1] = BG_VALUE;
    }

    k = 0;
    for (int q = 0; q <= n - 1; q++) {
      while (z[k+1] < q) k++;
      d[q] = (q - v[k]) * (q - v[k]) + f[v[k]];
    }

    return d;
  }

  private static void dt(FloatProcessor ip) {
    int width = ip.getWidth();
    int height = ip.getHeight();
    float[] f = new float[Math.max(width, height)];

    // transform along columns
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) f[y] = ip.getf(x, y);
      float[] d = dt(f, height);
      for (int y = 0; y < height; y++) ip.setf(x, y, d[y]);
    }

    // transform along rows
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) f[x] = ip.getf(x, y);
      float[] d = dt(f, width);
      for (int x = 0; x < width; x++) ip.setf(x, y, d[x]);
    }
  }

/*======================================================================*/
/*!
 *   Binary 2D Euclidean distance transform. The given fg value is treated
 *   as foreground, all other values as background. This method computes
 *   the euclidean distance to the nearest foreground pixel inline, i.e.
 *   the given FloatProcessor will contain the distance transform. For
 *   operator chaining it is also returned.
 *
 *   \param ip The FloatProcessor to compute the distance transform
 *   \param fg The value of foreground pixels
 *
 *   \return The FloatProcessor containing the distance transform
 */
/*======================================================================*/
  public static FloatProcessor getDistanceToForegroundPixels(
      FloatProcessor ip, float fg) {
    for (int y = 0; y < ip.getHeight(); y++) {
      for (int x = 0; x < ip.getWidth(); x++) {
        if (ip.getf(x, y) == fg) ip.setf(x, y, 0);
        else ip.setf(x, y, BG_VALUE);
      }
    }
    dt(ip);
    float[] pixels = (float[])ip.getPixels();
    for (int i = 0; i < ip.getHeight() * ip.getWidth(); i++)
        pixels[i] = (float)Math.sqrt(pixels[i]);
    return ip;
  }

/*======================================================================*/
/*!
 *   Binary 2D Euclidean distance transform. The given fg value is treated
 *   as foreground, all other values as background. This method computes
 *   the euclidean distance to the nearest background pixel inline, i.e.
 *   the given FloatProcessor will contain the distance transform. For
 *   operator chaining it is also returned.
 *
 *   \param ip The FloatProcessor to compute the distance transform
 *   \param fg The value of foreground pixels
 *
 *   \return The FloatProcessor containing the distance transform
 */
/*======================================================================*/
  public static FloatProcessor getDistanceToBackgroundPixels(
      FloatProcessor ip, float fg) {
    for (int y = 0; y < ip.getHeight(); y++) {
      for (int x = 0; x < ip.getWidth(); x++) {
        if (ip.getf(x, y) != fg) ip.setf(x, y, 0);
        else ip.setf(x, y, BG_VALUE);
      }
    }
    dt(ip);
    float[] pixels = (float[])ip.getPixels();
    for (int i = 0; i < ip.getHeight() * ip.getWidth(); i++)
        pixels[i] = (float)Math.sqrt(pixels[i]);
    return ip;
  }

}
