/*
 * Created on Mar 12, 2007
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package santa.simulator;

import java.util.logging.Logger;
import static santa.simulator.Simulator.readableByteCount;
import static santa.simulator.Simulator.usedMemory;
import santa.simulator.fitness.FitnessFunction;
import santa.simulator.genomes.GenePool;
import santa.simulator.mutators.Mutator;
import santa.simulator.population.Population;
import santa.simulator.replicators.Replicator;
import santa.simulator.samplers.SamplingSchedule;

public class SimulationEpoch {
    private String name;
    private int generationCount;
    private FitnessFunction fitnessFunction;
    private Mutator mutator;
    private Replicator replicator;
	private static Logger memlogger = Simulator.memlogger;

    public SimulationEpoch(String name, int generationCount,
            FitnessFunction fitnessFunction, Mutator mutator,
            Replicator replicator) {
        this.name = name;
        this.generationCount = generationCount;
        this.fitnessFunction = fitnessFunction;
        this.mutator = mutator;
        this.replicator = replicator;
    }

    public int run(Simulation simulation, Logger logger, int startGeneration) {//TODO change the popID here from an int to something else
        System.err.println("Starting epoch: " + (name != null ? name : "(unnamed)"));

        Population population;
        GenePool genePool = simulation.getGenePool();
        SamplingSchedule samplingSchedule = simulation.getSamplingSchedule();

        final int endGeneration = startGeneration + generationCount;

		memlogger.fine("@start of Epoch Memory used = " + readableByteCount(usedMemory()));

        for (int generation = startGeneration; generation < endGeneration; ++generation) {
            if (simulation.getPopulation() != null){
                //running solo pop mode
                this.doGeneration(generation, simulation.getPopulation(), -1, startGeneration, genePool, logger, samplingSchedule);//TODO popID -1 thing
        
                if(simulation.getPopulation().getCurrentGeneration().size() == 0) {
                    return generation;
                }
            } else{
                //multipop mode
                for (int popID : simulation.getPopulationIDs()){
                    population = simulation.getPopulationByID(popID);
                    this.doGeneration(generation, population, popID, startGeneration, genePool, logger, samplingSchedule);
            
                    if(population.getCurrentGeneration().size() == 0) {
                        return generation;//TODO this will break when any single population goes extinct
                    };

                }
            }
        }

        return endGeneration;
    }

    private void doGeneration(int generation, Population population, int popID, int startGeneration, GenePool genePool, Logger logger, SamplingSchedule samplingSchedule){

        EventLogger.setEpoch(generation);

        fitnessFunction.updateGeneration(generation, population);

        if (generation == startGeneration) {
            // adapt to this epoch, and the new generation
            population.updateAllFitnesses(fitnessFunction);
            
            System.err.println("Initial population ID = " + popID + 
                    ",  fitness = " + population.getMeanFitness() +
                    ", distance = " + population.getMeanDistance() +
                    ", max freq = " + population.getMaxFrequency() +
                    ", genepool size = " + genePool.getUniqueGenomeCount() +
                    " (" + genePool.getUnusedGenomeCount() + " available)");                
        }

        population.selectNextGeneration(generation, replicator, mutator, fitnessFunction);
        
        if (generation % 100 == 0) {
            if (population.getPhylogeny() != null)
                population.getPhylogeny().pruneDeadLineages();

            memlogger.finest("Generation "+ generation +
                            " used memory: " + readableByteCount(usedMemory()));

            System.err.print("Population " + popID + " Generation " + generation + ":  fitness = " + population.getMeanFitness() +
                    ", distance = " + population.getMeanDistance() +
                    ", max freq = " + population.getMaxFrequency() +
                    ", genepool size = " + genePool.getUniqueGenomeCount() +
                    " (" + genePool.getUnusedGenomeCount() + " available)");
            if (population.getPhylogeny() != null) {
                population.getPhylogeny().pruneDeadLineages();
                System.err.println(", phylogeny size = " + population.getPhylogeny().getSize() +
                    " (used = " + population.getPhylogeny().getLineageCount()+ ")" +
                    ", tmrca = " + population.getPhylogeny().getMRCA().getGeneration() );
            } else
                System.err.println();
        } else {
            logger.finest("Population " + popID + "Generation " + generation + ":  fitness = " + population.getMeanFitness() +
                    ", distance = " + population.getMeanDistance() +
                    ", max freq = " + population.getMaxFrequency() +
                    ", genepool size= " + genePool.getUniqueGenomeCount() +
                    "(" + genePool.getUnusedGenomeCount() + " available)");                
        }

        samplingSchedule.doSampling(generation, population);
    }

    public FitnessFunction getFitnessFunction() {
        return fitnessFunction;
    }

    public Mutator getMutator() {
        return mutator;
    }

    public Replicator getReplicator() {
        return replicator;
    }

}
