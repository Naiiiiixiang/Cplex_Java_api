package examples;

/* --------------------------------------------------------------------------
 * File: CutStock.java
 * Version 12.9.0  
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2001, 2019. All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * --------------------------------------------------------------------------
 */

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

import ilog.concert.*;
import ilog.cplex.*;

class CutStock {
    static double RC_EPS = 1.0e-6;
    
    static double rollWidth;
    static double[] size;
    static double[] amount;

    static void readData(String fileName) throws IOException, InputDataReader.InputDataReaderException {
        InputDataReader reader = new InputDataReader(fileName);

        rollWidth = reader.readDouble();
        size = reader.readDoubleArray();
        amount = reader.readDoubleArray();
    }

    /**
     * Description .<br>
     * 
     * @param rmlpSolver
     * @param cut
     * @param fill
     * @throws IloException
     */
    static void report1(IloCplex rmlpSolver, IloNumVarArray cut, IloRange[] fill) throws IloException {
        System.out.println();
        System.out.println("Using " + rmlpSolver.getObjValue() + " rolls");

        System.out.println();
        for (int j = 0; j < cut.getSize(); j++) {
            System.out.println("  Cut" + j + " = " + rmlpSolver.getValue(cut.getCutNum(j)));
        }
        System.out.println();

        for (int i = 0; i < fill.length; i++) {
            System.out.println("  Fill" + i + " = " + rmlpSolver.getDual(fill[i]));
        }
        System.out.println();
    }

    static void report2(IloCplex patSolver, IloNumVar[] times) throws IloException {
        System.out.println();
        System.out.println("Reduced cost is " + patSolver.getObjValue());

        System.out.println();
        if (patSolver.getObjValue() <= -RC_EPS) {
            for (int i = 0; i < times.length; i++) {
                System.out.println("  Use" + i + " = " + patSolver.getValue(times[i]));
            }
            System.out.println();
        }
    }

    static void report3(IloCplex rmlpSolver, IloNumVarArray cut) throws IloException {
        System.out.println();
        System.out.println("Best integer solution uses " + rmlpSolver.getObjValue() + " rolls");
        System.out.println();
        for (int j = 0; j < cut.getSize(); j++) {
            System.out.println("  Cut" + j + " = " + rmlpSolver.getValue(cut.getCutNum(j)));
        }
    }

    /**
     * ?????????????????????????????? .<br>
     * 
     * @author xiong
     * @version v1.0
     * @since JDK1.8
     */
    static class IloNumVarArray {
        int num = 0;
        IloNumVar[] array = new IloNumVar[32];
        ArrayList<double[]> patterns = new ArrayList<double[]>();

        void add(IloNumVar ivar) {
            // resizing the array
            if (num >= array.length) {
                IloNumVar[] newArray = new IloNumVar[2 * array.length];
                System.arraycopy(array, 0, newArray, 0, num);
                array = newArray;
            }
            array[num++] = ivar;
        }
        
        void add(double[] pattern) {
            patterns.add(pattern);
        }

        IloNumVar getCutNum(int i) {
            return array[i];
        }
        
        double[] getPattern(int i) {
            return patterns.get(i);
        }

        int getSize() {
            return num;
        }
    }
    
    
    public static void main(String[] args) {
        String datafile = "./data/cutstock.dat";
        try {
            if (args.length > 0) {
                datafile = args[0];
            }
            readData(datafile);

            // RMLP???MLP???????????????
            IloCplex rmlpSolver = new IloCplex();
            
            // RMLP Model
            IloObjective rollsUsed = rmlpSolver.addMinimize();
            IloRange[] fill = new IloRange[amount.length];
            for (int f = 0; f < amount.length; f++) {
                // MLP Model?????????????????????????????????
                fill[f] = rmlpSolver.addRange(amount[f], Double.MAX_VALUE);
            }
            
            // ???????????????????????????????????????
            IloNumVarArray cutPattern = new IloNumVarArray();
            
            /*
             * RMLP Model
             * ???????????????????????????????????????????????????????????????????????????????????????????????????
             * ?????????????????????nWdth????????????????????????????????????????????????????????????????????????????????????
             */
            int nWdth = size.length;
            for (int j = 0; j < nWdth; j++) {
                cutPattern.add(rmlpSolver.numVar(
                        rmlpSolver.column(rollsUsed, 1.0).and(rmlpSolver.column(fill[j], (int) (rollWidth / size[j]))),
                        0.0, Double.MAX_VALUE));
                
                double[] pattern = new double[nWdth];
                pattern[j] = (int) (rollWidth / size[j]);
                cutPattern.add(pattern);
            }
            
            // ?????????????????? - ??????????????????
            rmlpSolver.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Primal);

            // Pricing Model
            IloCplex patSolver = new IloCplex();
            IloObjective reducedCost = patSolver.addMinimize();
            // ??????????????????????????????????????????????????????????????????
            IloNumVar[] times = patSolver.numVarArray(nWdth, 0., Double.MAX_VALUE, IloNumVarType.Int);
            // Pricing Problem?????????????????????
            patSolver.addRange(-Double.MAX_VALUE, patSolver.scalProd(size, times), rollWidth);

            // ???????????????????????????
            double[] newPatt = new double[nWdth];
            for (;;) {
                
                // Solve the RMLP to get the primal(upper bound) and dual solution
                rmlpSolver.solve();
                report1(rmlpSolver, cutPattern, fill);

                /// Solve the Pricing Problem??????????????????
                double[] price = rmlpSolver.getDuals(fill);
                // Pricing Problem ?????????????????????
                reducedCost.setExpr(patSolver.diff(1., patSolver.scalProd(times, price)));
                // ??????Pricing Problem
                patSolver.solve();
                report2(patSolver, times);
                
                // ???Pricing Problem??????????????????0??????MLP?????????????????????????????????????????????????????????????????????
                if (patSolver.getObjValue() > -RC_EPS) {
                    break;
                }
                
                newPatt = patSolver.getValues(times);

                // ??????????????????
                cutPattern.add(newPatt);
                
                /*
                 * ??????????????????????????????RMLP??????????????????
                 * 1.?????????
                 * 2.??????
                 * 3.??????
                 * ?????????column???????????????
                 */
                IloColumn column = rmlpSolver.column(rollsUsed, 1.);
                for (int p = 0; p < newPatt.length; p++) {
                    // ????????????
                    column = column.and(rmlpSolver.column(fill[p], newPatt[p]));
                }
                // ??????????????????????????????????????????
                cutPattern.add(rmlpSolver.numVar(column, 0., Double.MAX_VALUE));
            }
            
            for (int i = 0; i < cutPattern.getSize(); i++) {
                // ??????????????????int??????
                rmlpSolver.add(rmlpSolver.conversion(cutPattern.getCutNum(i), IloNumVarType.Int));
                
            }
            
            rmlpSolver.solve();
            rmlpSolver.exportModel("model2.lp");
            
            System.out.println("Solution status: " + rmlpSolver.getStatus());
            
            System.out.println("Cut plan: ");
            for (int i = 0; i < cutPattern.num; i++) {
                System.out.print("Cut" + i + " \n= " + rmlpSolver.getValue(cutPattern.getCutNum(i)));
                System.out.println("    Pattern" + " = " + Arrays.toString(cutPattern.getPattern(i)));
            }
            
            // ???????????????
            rmlpSolver.end();
            patSolver.end();
        } catch (IloException exc) {
            System.err.println("Concert exception '" + exc + "' caught");
        } catch (IOException exc) {
            System.err.println("Error reading file " + datafile + ": " + exc);
        } catch (InputDataReader.InputDataReaderException exc) {
            System.err.println(exc);
        }
    }
}

/*
 * Example Input file: 
 * 115
 * [25, 40, 50, 55, 70]
 * [50, 36, 24, 8, 30]
 */
