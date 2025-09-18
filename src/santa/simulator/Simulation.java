package santa.simulator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.logging.Logger;

import santa.simulator.genomes.GenePool;
import santa.simulator.genomes.GenomeDescription;
import santa.simulator.genomes.Sequence;
import santa.simulator.phylogeny.Phylogeny;
import santa.simulator.population.PopulationGrowth;
import santa.simulator.population.Population;
import santa.simulator.samplers.SamplingSchedule;
import santa.simulator.selectors.Selector;

/**
 * @author Andrew Rambaut
 * @author Alexei Drummond
 * @version $Id: Simulation.java,v 1.11 2006/07/19 12:53:05 kdforc0 Exp $
 */
public class Simulation {

    private final int populationSize;
    private final InoculumType inoculumType;
    private final GenePool genePool;
    private final List<SimulationEpoch> epochs;
    private final Selector selector;
    private final SamplingSchedule samplingSchedule;

    private final Population population;
    private final HashMap<Integer, Population> populationList;

	public enum InoculumType {
		NONE,
		CONSENSUS,
		RANDOM,
		ALL
	};
	
    //Default constructor
    public Simulation (
            int populationSize,
            Selector selector,
            PopulationGrowth growth,
            InoculumType inoculumType,
            GenePool genePool,
            List<SimulationEpoch> epochs,
            SamplingSchedule samplingSchedule) {

        this.populationSize = populationSize;
        this.inoculumType = inoculumType;
        this.epochs = epochs;
        this.samplingSchedule = samplingSchedule;
        this.genePool = genePool;
        this.selector = selector;

        this.population = new Population(genePool, selector, growth, samplingSchedule.isSamplingTrees() ? new Phylogeny(populationSize) : null);
        this.populationList = null;//TODO: remove this simplify constructor to just hashmap version
    }

    /*
    * Constructor for use when simulating multiple populations.
    */
    public Simulation (
            int populationSize,
            Selector selector,
            PopulationGrowth growth,
            InoculumType inoculumType,
            GenePool genePool,
            List<SimulationEpoch> epochs,
            SamplingSchedule samplingSchedule,
            int numPops) {

        this.populationSize = populationSize;
        this.inoculumType = inoculumType;
        this.epochs = epochs;
        this.samplingSchedule = samplingSchedule;
        this.genePool = genePool;
        this.selector = selector;

        this.population = null;
        this.populationList = new HashMap<Integer, Population>();
        int popID = 0;//TODO: change this to a string ID provided by the user
        Population population;
        while (popID < numPops){
            population = new Population(genePool, selector, growth, samplingSchedule.isSamplingTrees() ? new Phylogeny(populationSize) : null);
            this.populationList.put(popID, population);
            popID++;
        }
    }
    
    public void run(int replicate, Logger logger) {

        samplingSchedule.initialize(replicate);

        EventLogger.setReplicate(replicate);

        logger.finer("Initializing population: " + populationSize + " viruses.");

	    List<Sequence> inoculum = new ArrayList<Sequence>();
	    if (inoculumType == InoculumType.CONSENSUS) {
		    inoculum.add(GenomeDescription.getConsensus());
	    } else if (inoculumType == InoculumType.ALL) {
		    inoculum.addAll(GenomeDescription.getSequences());
	    } else if (inoculumType == InoculumType.RANDOM) {
		    List<Sequence> sequences = GenomeDescription.getSequences();
		    if (sequences.size() == 1) {
			    inoculum.add(sequences.get(0));
		    } else {
		        inoculum.add(sequences.get(Random.nextInt(0, sequences.size() - 1)));
		    }
	    } else { // NONE
		    // do nothing
	    }

        //check if we are running one or several populations // TODO: maybe just simplify and make everything use the HashMap version
        if(this.populationList == null){
            population.initialize(inoculum, populationSize);
        } else{ //multiple population mode
            this.populationList.forEach((popID, population) -> {
                population.initialize(inoculum, populationSize);//TODO: this is assuming all pops have the same size
            });
        }

        int generation = 1;

        int epochCount = 0;

        for (SimulationEpoch epoch:epochs) {
            EventLogger.setEpoch(epochCount);

            generation = epoch.run(this, logger, generation);
            if(population != null && population.getCurrentGeneration().isEmpty()) {
                System.err.println("Population crashed after "+generation+" generations.");
                return;
            } else {
                for(int popID : populationList.keySet()){
                    if(getPopulationByID(popID).getCurrentGeneration().isEmpty()){
                        System.err.println("Population " + popID + " crashed after "+generation+" generations.");
                        return;
                    }
                }
            }

            epochCount++;
        }
        samplingSchedule.cleanUp();
    }

    public GenePool getGenePool() {
        return genePool;
    }

    public Population getPopulation() {
        return population;
    }

    public Population getPopulationByID(int id){
        if(this.populationList != null){
            Population pop = this.populationList.get(id);
            if(pop == null){
                System.err.println("Attempt to access unknown population with ID: "+id+" .");
                System.exit(0);//TODO: should this be a different error code?
            }
            return pop;
        }
        return this.getPopulation();//return when only one pop exists
    }

    public Set<Integer> getPopulationIDs(){
        return populationList.keySet();
    }

    public int getPopulationSize() {
        return populationSize;
    }

    public SamplingSchedule getSamplingSchedule() {
        return samplingSchedule;
    }

}
