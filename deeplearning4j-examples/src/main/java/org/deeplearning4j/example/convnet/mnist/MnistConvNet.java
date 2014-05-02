package org.deeplearning4j.example.convnet.mnist;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.deeplearning4j.datasets.DataSet;
import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.datasets.mnist.draw.DrawMnistGreyScale;
import org.deeplearning4j.distributions.Distributions;
import org.deeplearning4j.nn.NeuralNetwork;
import org.deeplearning4j.nn.Tensor;
import org.deeplearning4j.plot.FilterRenderer;
import org.deeplearning4j.plot.NeuralNetPlotter;
import org.deeplearning4j.rbm.ConvolutionalRBM;
import org.deeplearning4j.rbm.RBM;
import org.deeplearning4j.util.ImageLoader;
import org.deeplearning4j.util.MatrixUtil;
import org.jblas.DoubleMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 *
 */
public class MnistConvNet {

    private static Logger log = LoggerFactory.getLogger(MnistConvNet.class);

    public static void main(String[] args) throws Exception {
        RandomGenerator gen = new MersenneTwister(123);

        int rows = 7;
        int cols = 7;

        ConvolutionalRBM r = new ConvolutionalRBM
                .Builder().withFilterSize(new int[]{rows, cols})
                .withNumFilters(9).withStride(new int[]{2, 2}).withVisible(RBM.VisibleUnit.BINARY)
                .withVisibleSize(new int[]{28,28})
                .withLossFunction(NeuralNetwork.LossFunction.RECONSTRUCTION_CROSSENTROPY)
                .numberOfVisible(28).numHidden(28)
                .withMomentum(0.5)
                .build();


        //batches of 10, 60000 examples total
        DataSetIterator iter = new MnistDataSetIterator(1,10);
        for(int i = 0; i < 10 ;i++) {
            while(iter.hasNext()) {
                DataSet next = iter.next();
                log.info("This is a " + next.labelDistribution());
                DoubleMatrix reshape = next.getFirst().reshape(28,28);
                for(int j = 0; j < 10; j++)
                    r.train(reshape, 1e-2, new Object[]{1, 1e-2, 20});


                Tensor reshapePool = r.poolGivenVis(reshape);

                drawFilters(r,rows,cols);
                //drawSample(r,rows,cols,reshape);
                for(int j = 0; j < reshapePool.slices(); j++)
                    drawSample(r,reshapePool.getSlice(j).rows,reshapePool.getSlice(j).columns,reshapePool.getSlice(j));


            }







            iter.reset();
        }






    }



    public static void drawSample(ConvolutionalRBM r,int rows, int cols,DoubleMatrix input) throws Exception {

        DoubleMatrix draw = input.dup();
        draw.muli(255);
        DrawMnistGreyScale greyScale = new DrawMnistGreyScale(input,input.rows,input.columns);
        greyScale.readjustToData();
        greyScale.draw();

        log.info("Draw sum " + draw.sum());
        BufferedImage img = ImageLoader.toImage(input);
        File write = new File("newtmpfile-pool.png");

        Graphics2D g = img.createGraphics();
        g.drawImage(img, 0, 0, cols,rows, null);
        g.setComposite(AlphaComposite.Src);

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);



        g.dispose();



        ImageIO.write(img, "png", write);

    }

    public static void drawFilters(ConvolutionalRBM r,int rows, int cols) throws Exception {
        Tensor W = (Tensor) r.getW().dup();

        DoubleMatrix draw =  W.reshape(W.rows() * W.columns(),W.slices());
        draw.muli(255);
        FilterRenderer render = new FilterRenderer();
        BufferedImage img = render.renderFilters(draw,"tmpfile.png",rows,cols);
        BufferedImage resizedImage = new BufferedImage(49, 49, img.getType());
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(img, 0, 0, 49, 49, null);
        g.setComposite(AlphaComposite.Src);

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);



        g.dispose();
        File write = new File("newtmpfile.png");
        if(write.exists())
            write.delete();
        ImageIO.write(resizedImage, "png", write);

    }

}