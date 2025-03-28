package src.pas.pokemon.agents;


// SYSTEM IMPORTS....feel free to add your own imports here! You may need/want to import more from the .jar!
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Move.Category;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Flag;
import edu.bu.pas.pokemon.core.enums.Target;
import edu.bu.pas.pokemon.core.enums.Type;

import edu.bu.pas.pokemon.core.callbacks.Callback;
import edu.bu.pas.pokemon.core.callbacks.MultiCallbackCallback;
import edu.bu.pas.pokemon.core.callbacks.ResetLastDamageDealtCallback;
import edu.bu.pas.pokemon.core.callbacks.DoDamageCallback;
import edu.bu.pas.pokemon.utils.Pair;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


// JAVA PROJECT IMPORTS


public class TreeTraversalAgent
    extends Agent
{

	private class StochasticTreeSearcher
        extends Object
        implements Callable<Pair<MoveView, Long> >  // so this object can be run in a background thread
	{
        private class Node
        {
            private MoveViewSpecial myMove;
            private MoveViewSpecial oppMove;
            private List<Node> children;
            private boolean isChanceChild;
            private double probability;

            public Node() { 
                this.isChanceChild = false;
                this.children = new LinkedList<>();
            }

            public Node(MoveViewSpecial myMove) 
            { 
                this.myMove = myMove;
                this.isChanceChild = false;
                this.children = new LinkedList<>();
            }

            public Node(MoveViewSpecial myMove, MoveViewSpecial oppMove) 
            { 
                this.myMove = myMove;
                this.oppMove = oppMove;
                this.isChanceChild = false;
                this.children = new LinkedList<>();
            }

            public Node(MoveViewSpecial myMove, MoveViewSpecial oppMove, double probability) 
            { 
                this.myMove = myMove;
                this.oppMove = oppMove;
                this.probability = probability;
                this.isChanceChild = true;
                this.children = new LinkedList<>();
            }

            public MoveViewSpecial getMyMove() { return this.myMove; }
            public MoveViewSpecial getOppMove() { return this.oppMove; }
            public double getProbability() { return this.probability; }
            public Iterator<Node> getChildren() { return this.children.iterator(); }
            public Node getLastChild() { return this.children.get(this.children.size() - 1); }
            public boolean getIsChance() { return this.isChanceChild; }
            public void addChild(Node newChild) { this.children.add(newChild); }
        }

        public class MoveViewSpecial
        {
            private MoveView move;
            private double utility;

            public MoveViewSpecial(MoveView move)
            {
                this.move = move;
            }

            public void setUtility(double utility) { this.utility = utility; }
            public double getUtility() { return this.utility; }
            public MoveView getMove() { return this.move; }
        }
    
        // TODO: feel free to add any fields here! If you do, you should probably modify the constructor
        // of this class and add some getters for them. If the fields you add aren't final you should add setters too!
		private final BattleView rootView;
        private final int maxDepth;
        private final int myTeamIdx;
        private boolean myTeamGoesFirst;
        private int myMoveOppIdx;

        // If you change the parameters of the constructor, you will also have to change
        // the getMove(...) method of TreeTraversalAgent!
		public StochasticTreeSearcher(BattleView rootView, int maxDepth, int myTeamIdx)
        {
            this.rootView = rootView;
            this.maxDepth = maxDepth;
            this.myTeamIdx = myTeamIdx;
        }

        // Getter methods. Since the default fields are declared final, we don't need setters
        // but if you make any fields that aren't final you should give them setters!
		public BattleView getRootView() { return this.rootView; }
        public int getMaxDepth() { return this.maxDepth; }
        public int getMyTeamIdx() { return this.myTeamIdx; }
        public boolean getMyTeamGoesFirst() { return this.myTeamGoesFirst; }
        public int getMyMoveOppIdx() { return this.myMoveOppIdx; }

        public void setMyTeamGoesFirst(boolean status) { this.myTeamGoesFirst = status; }
        public void setMyMoveOppIdx(int idx) { this.myMoveOppIdx = idx; }

		/**
		 * TODO: implement me!
		 * This method should perform your tree-search from the root of the entire tree.
         * You are welcome to add any extra parameters that you want! If you do, you will also have to change
         * The call method in this class!
		 * @param node the node to perform the search on (i.e. the root of the entire tree)
		 * @return The MoveView that your agent should execute
		 */
        public MoveView stochasticTreeSearch(BattleView rootView) //, int depth)
        {
            return treeSearchRecurse(rootView, new Node(), 0).getMove();
        }

        public MoveViewSpecial treeSearchRecurse(BattleView rootView, Node root, int stage)
        {
            MoveViewSpecial bestMove = null;
            double bestUtility = 0;
            double utility = 0;
            int nextStage = -1;
            int loseTurnNextStage = -1;
            PokemonView myPokemon = null;
            PokemonView oppPokemon = null;
            Node childNode = null;
            MoveViewSpecial myMove = null;
            MoveViewSpecial oppMove = null;
            Iterator<Pair<Double, BattleView>> effectIter = null;

            switch (stage)
            {
                // Pick max of my possible moves
                case 0:
                   myPokemon = getMyTeamView(rootView).getActivePokemonView();
                   Iterator<MoveView> myMovesIter = myPokemon.getAvailableMoves().iterator();
                   bestMove = null;
                   bestUtility = Double.NEGATIVE_INFINITY;
                   while (myMovesIter.hasNext())
                   {
                    MoveViewSpecial currentMove = new MoveViewSpecial(myMovesIter.next());
                    Node currentNode = new Node(currentMove);
                    root.addChild(currentNode);
                    double currentUtility = treeSearchRecurse(rootView, root.getLastChild(), 1).getUtility();
                    if (currentUtility > bestUtility)
                    {
                        bestUtility = currentUtility;
                        bestMove = currentMove;
                    }
                   }
                   
                   bestMove.setUtility(bestUtility);
                   return bestMove;
                // Pick min of opponent's possible moves
                case 1:
                   oppPokemon = getOpponentTeamView(rootView).getActivePokemonView();
                   Iterator<MoveView> oppMovesIter = oppPokemon.getAvailableMoves().iterator();
                   bestMove = null;
                   bestUtility = Double.POSITIVE_INFINITY;
                   while (oppMovesIter.hasNext())
                   {
                    MoveViewSpecial currentMove = new MoveViewSpecial(oppMovesIter.next());
                    Node currentNode = new Node(root.getMyMove(), currentMove);
                    root.addChild(currentNode);
                    double currentUtility = treeSearchRecurse(rootView, root.getLastChild(), 2).getUtility();
                    if (currentUtility < bestUtility)
                    {
                        bestUtility = currentUtility;
                        bestMove = currentMove;
                    }
                   }
                   
                   bestMove.setUtility(bestUtility);
                   return bestMove;
                // Chance node based on who goes first
                case 2:
                    myMove = root.getMyMove();
                    oppMove = root.getOppMove();
                    myPokemon = getMyTeamView(rootView).getActivePokemonView();
                    oppPokemon = getOpponentTeamView(rootView).getActivePokemonView();
                    double mySpeed = (double) myPokemon.getCurrentStat(Stat.SPD);
                    double oppSpeed = (double) oppPokemon.getCurrentStat(Stat.SPD);
                    utility = 0;
                    if (myPokemon.getNonVolatileStatus() == NonVolatileStatus.PARALYSIS) mySpeed *= 0.75;
                    if (oppPokemon.getNonVolatileStatus() == NonVolatileStatus.PARALYSIS) oppSpeed *= 0.75;

                    if (myMove.getMove().getPriority() > oppMove.getMove().getPriority())
                    {
                        this.setMyTeamGoesFirst(true);
                        nextStage = 3;
                    }
                    else if (myMove.getMove().getPriority() < oppMove.getMove().getPriority())
                    {
                        this.setMyTeamGoesFirst(false);
                        nextStage = 7;
                    }
                    else if (mySpeed > oppSpeed)
                    {
                        this.setMyTeamGoesFirst(true);
                        nextStage = 3;
                    }
                    else if (oppSpeed > mySpeed)
                    {
                        this.setMyTeamGoesFirst(true);
                        nextStage = 7;
                    }

                    if (nextStage > -1)
                    {
                        childNode = new Node(myMove, oppMove);
                        root.addChild(childNode);
                        utility = treeSearchRecurse(rootView, root.getLastChild(), nextStage).getUtility();
                    }
                    else
                    {
                        this.setMyTeamGoesFirst(true);
                        childNode = new Node(myMove, oppMove, 0.5);
                        root.addChild(childNode);
                        utility = treeSearchRecurse(rootView, root.getLastChild(), 3).getUtility() 
                            * root.getLastChild().getProbability();
                        
                        this.setMyTeamGoesFirst(false);
                        childNode = new Node(myMove, oppMove, 0.5);
                        root.addChild(childNode);
                        utility += treeSearchRecurse(rootView, root.getLastChild(), 7).getUtility() 
                            * root.getLastChild().getProbability(); 
                    }
                    
                    myMove.setUtility(utility);
                    return myMove;
                // Assuming I go first, check for paralysis to see we can still do move
                case 3:
                    myMove = root.getMyMove();
                    oppMove = root.getOppMove();
                    myPokemon = getMyTeamView(rootView).getActivePokemonView();
                    utility = 0;

                    if (this.getMyTeamGoesFirst())
                    {
                        loseTurnNextStage = 7;
                    }
                    else
                    {
                        loseTurnNextStage = 8;
                    }

                    if (myPokemon.getNonVolatileStatus() == NonVolatileStatus.PARALYSIS)
                    {
                        childNode = new Node(myMove, oppMove, 0.25);
                        root.addChild(childNode);
                        utility = treeSearchRecurse(rootView, root.getLastChild(), loseTurnNextStage).getUtility() 
                            * root.getLastChild().getProbability();
                        childNode = new Node(myMove, oppMove, 0.75);
                        root.addChild(childNode);
                        utility += treeSearchRecurse(rootView, root.getLastChild(), 4).getUtility() 
                            * root.getLastChild().getProbability(); 
                    }
                    else
                    {
                        childNode = new Node(myMove, oppMove);
                        root.addChild(childNode);
                        utility = treeSearchRecurse(rootView, root.getLastChild(), 4).getUtility();
                    }
                    
                    myMove.setUtility(utility);
                    return myMove;
                // Now apply preMove condition to check for sleep/freeze
                case 4:
                    myMove = root.getMyMove();
                    oppMove = root.getOppMove();
                    List<Pair<Double, BattleView>> preMove = rootView.applyPreMoveConditions(this.getMyTeamIdx());
                    utility = 0;

                    if (this.getMyTeamGoesFirst())
                    {
                        loseTurnNextStage = 7;
                    }
                    else
                    {
                        loseTurnNextStage = 8;
                    }

                    if (preMove.size() == 1)
                    {
                        childNode = new Node(myMove, oppMove);
                        root.addChild(childNode);
                        utility = treeSearchRecurse(preMove.get(0).getSecond(), root.getLastChild(), 5).getUtility();
                    }
                    else
                    {
                        for (int i = 0; i < preMove.size(); i++)
                        {
                            if (preMove.get(i).getFirst() == 0.098)
                            {
                                childNode = new Node(myMove, oppMove, 0.098);
                                root.addChild(childNode);
                                utility += treeSearchRecurse(preMove.get(0).getSecond(), root.getLastChild(), 5).getUtility()
                                    * root.getLastChild().getProbability();
                            }
                            else
                            {
                                childNode = new Node(myMove, oppMove, 0.902);
                                root.addChild(childNode);
                                utility += treeSearchRecurse(preMove.get(0).getSecond(), root.getLastChild(), loseTurnNextStage).getUtility()
                                    * root.getLastChild().getProbability();
                            }
                        }
                    }
                    
                    myMove.setUtility(utility);
                    return myMove;
                // Check for confusion
                case 5:
                    myMove = root.getMyMove();
                    oppMove = root.getOppMove();
                    myPokemon = getMyTeamView(rootView).getActivePokemonView();
                    utility = 0;

                    if (!myPokemon.getFlag(Flag.CONFUSED))
                    {
                        this.setMyMoveOppIdx(getOpponentTeamView(rootView).getBattleIdx());
                        childNode = new Node(myMove, oppMove);
                        root.addChild(childNode);
                        utility = treeSearchRecurse(rootView, root.getLastChild(), 6).getUtility();
                    }
                    else
                    {
                        this.setMyMoveOppIdx(getOpponentTeamView(rootView).getBattleIdx());
                        childNode = new Node(myMove, oppMove, 0.5);
                        root.addChild(childNode);
                        utility = treeSearchRecurse(rootView, root.getLastChild(), 6).getUtility()
                            * root.getLastChild().getProbability(); 
                        
                        Move selfHurtMove = new Move(
                            "SelfDamage", // move name
                            Type.NORMAL, // damage type (should be typeless but we'll tell the
                            // to ignore STAB and type terms in damage calculation
                            Category.PHYSICAL, // move category
                            40, // base power for hurting yourself from confusion is 40
                            null, // infinite accuracy
                            Integer.MAX_VALUE, // number of uses
                            1, // critical hit ratio
                            0 // priority
                        ).addCallback(
                            new MultiCallbackCallback(
                                new ResetLastDamageDealtCallback(), // new damage so reset old value
                                new DoDamageCallback(
                                    Target.CASTER, // hurt yourself
                                    false, // dont include STAB term in damage calc
                                    false, // ignore type terms in damage calculation
                                    true // damage ignores substitutes
                                )
                            )
                        );

                        this.setMyMoveOppIdx(getMyTeamIdx());
                        myMove = new MoveViewSpecial(new MoveView(selfHurtMove));
                        childNode = new Node(myMove, oppMove, 0.5);
                        root.addChild(childNode);
                        utility += treeSearchRecurse(rootView, root.getLastChild(), 6).getUtility()
                            * root.getLastChild().getProbability(); 
                    }
                    
                    myMove.setUtility(utility);
                    return myMove;
                // Apply my move
                case 6:
                    myMove = root.getMyMove();
                    oppMove = root.getOppMove();
                    utility = 0;
                    effectIter = myMove.getMove().getPotentialEffects(rootView, getMyTeamIdx(), this.getMyMoveOppIdx()).iterator();

                    if (this.getMyTeamGoesFirst())
                    {
                        nextStage = 7;
                    }
                    else
                    {
                        nextStage = 8;
                    }

                    while (effectIter.hasNext())
                    {
                        Pair<Double, BattleView> currentPair = effectIter.next();
                        childNode = new Node(myMove, oppMove, currentPair.getFirst());
                        root.addChild(childNode);
                        utility += treeSearchRecurse(currentPair.getSecond(), root.getLastChild(), nextStage).getUtility()
                            * root.getLastChild().getProbability();
                    }
                    
                    myMove.setUtility(utility);
                    return myMove;
                // Apply opponent's move
                case 7:
                    myMove = root.getMyMove();
                    oppMove = root.getOppMove();
                    utility = 0;
                    effectIter = oppMove.getMove().getPotentialEffects(rootView, getOpponentTeamView(rootView).getBattleIdx(), getMyTeamIdx()).iterator();

                    if (this.getMyTeamGoesFirst())
                    {
                        nextStage = 8;
                    }
                    else
                    {
                        nextStage = 3;
                    }

                    while (effectIter.hasNext())
                    {
                        Pair<Double, BattleView> currentPair = effectIter.next();
                        childNode = new Node(myMove, oppMove, currentPair.getFirst());
                        root.addChild(childNode);
                        utility += treeSearchRecurse(currentPair.getSecond(), root.getLastChild(), nextStage).getUtility()
                            * root.getLastChild().getProbability();
                    }
                    
                    myMove.setUtility(utility);
                    return myMove;
                // Calculate HP in current battleView and use that as utility
                default:
                    myMove = root.getMyMove();
                    utility = getMyTeamView(rootView).getActivePokemonView().getCurrentStat(Stat.HP) - getOpponentTeamView(rootView).getActivePokemonView().getCurrentStat(Stat.HP);
                    myMove.setUtility((double) utility);
                    //System.out.println(myMove.getUtility());
                    return myMove;
            }
        }

        @Override
        public Pair<MoveView, Long> call() throws Exception
        {
            double startTime = System.nanoTime();

            MoveView move = this.stochasticTreeSearch(this.getRootView());
            double endTime = System.nanoTime();

            return new Pair<MoveView, Long>(move, (long)((endTime-startTime)/1000000));
        }
		
	}

	private final int maxDepth;
    private long maxThinkingTimePerMoveInMS;

	public TreeTraversalAgent()
    {
        super();
        this.maxThinkingTimePerMoveInMS = 180000 * 2; // 6 min/move
        this.maxDepth = 1000; // set this however you want
    }

    /**
     * Some constants
     */
    public int getMaxDepth() { return this.maxDepth; }
    public long getMaxThinkingTimePerMoveInMS() { return this.maxThinkingTimePerMoveInMS; }

    @Override
    public Integer chooseNextPokemon(BattleView view)
    {
        // TODO: replace me! This code calculates the first-available pokemon.
        // It is likely a good idea to expand a bunch of trees with different choices as the active pokemon on your
        // team, and see which pokemon is your best choice by comparing the values of the root nodes.

        for(int idx = 0; idx < this.getMyTeamView(view).size(); ++idx)
        {
            if(!this.getMyTeamView(view).getPokemonView(idx).hasFainted())
            {
                return idx;
            }
        }
        return null;
    }

    /**
     * This method is responsible for getting a move selected via the minimax algorithm.
     * There is some setup for this to work, namely making sure the agent doesn't run out of time.
     * Please do not modify.
     */
    @Override
    public MoveView getMove(BattleView battleView)
    {

        // will run the minimax algorithm in a background thread with a timeout
        ExecutorService backgroundThreadManager = Executors.newSingleThreadExecutor();

        // preallocate so we don't spend precious time doing it when we are recording duration
        MoveView move = null;
        long durationInMs = 0;

        // this obj will run in the background
        StochasticTreeSearcher searcherObject = new StochasticTreeSearcher(
            battleView,
            this.getMaxDepth(),
            this.getMyTeamIdx()
        );

        // submit the job
        Future<Pair<MoveView, Long> > future = backgroundThreadManager.submit(searcherObject);

        try
        {
            // set the timeout
            Pair<MoveView, Long> moveAndDuration = future.get(
                this.getMaxThinkingTimePerMoveInMS(),
                TimeUnit.MILLISECONDS
            );

            // if we get here the move was chosen quick enough! :)
            move = moveAndDuration.getFirst();
            durationInMs = moveAndDuration.getSecond();

            // convert the move into a text form (algebraic notation) and stream it somewhere
            // Streamer.getStreamer(this.getFilePath()).streamMove(move, Planner.getPlanner().getGame());
        } catch(TimeoutException e)
        {
            // timeout = out of time...you lose!
            System.err.println("Timeout!");
            System.err.println("Team [" + (this.getMyTeamIdx()+1) + " loses!");
            System.exit(-1);
        } catch(InterruptedException e)
        {
            e.printStackTrace();
            System.exit(-1);
        } catch(ExecutionException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

        return move;
    }
}
