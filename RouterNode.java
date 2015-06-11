import javax.swing.*;        
import java.util.Arrays;
/* F.java has some issue ... uried using the java Formatter instead. */
/* But it required a lot of rework for the printing */
/* and went with the Format in F.java anyway. */
import java.util.Formatter; 

// #define NUM_NODES RouterSimulator.NUM_NODES ... eh, java can't pre-process

public class RouterNode {
    private int myID;
    private GuiTextArea myGUI;
    private RouterSimulator sim;

    private int[] costs = new int[RouterSimulator.NUM_NODES];
    // This is a table(matrix), not really a vector. Kinda confusing ... Oh well.
    private int[][] distanceVector = new int[RouterSimulator.NUM_NODES][RouterSimulator.NUM_NODES];

    /* Keep track of the minimal routes */
    private int[] minRoute = new int[RouterSimulator.NUM_NODES];
    private int[] minCosts = new int[RouterSimulator.NUM_NODES];
    private boolean poisonedReverse = false;

    //--------------------------------------------------
    public RouterNode(int ID, RouterSimulator sim, int[] costs) {
	myID = ID;
	this.sim = sim;
	myGUI =new GuiTextArea("  Output window for Router #"+ ID + "  ");
	System.arraycopy(costs, 0, this.costs, 0, RouterSimulator.NUM_NODES);

	/* Initialize the distance vector */
	init_distanceVector();
	
	/* Set node's DV to the direct link costs */
	System.arraycopy(costs, 0, distanceVector[myID], 0, RouterSimulator.NUM_NODES);
	System.arraycopy(costs, 0, minCosts, 0, RouterSimulator.NUM_NODES);
	
	/* If minimal routes exist, initialize them */
	for ( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    {
		if ( costs[i] == RouterSimulator.INFINITY  )
		    minRoute[i] = RouterSimulator.INFINITY;
		else
		    minRoute[i] = i;
	    }

	printDistanceTable();
	/* Send distance vector */
	sendDistanceVector();
    }

    //--------------------------------------------------
    public void recvUpdate(RouterPacket pkt) {
	
	/* copy the new mincost vector to the distance vector */
	System.arraycopy(pkt.mincost, 0, distanceVector[pkt.sourceid], 0, RouterSimulator.NUM_NODES);

	/* update new min-paths in the distance vector */
	if (updateDistancevector(pkt.sourceid, pkt.mincost) )
	    sendDistanceVector();
    }
  

    //--------------------------------------------------
    private void sendUpdate(RouterPacket pkt) 
    {
	sim.toLayer2(pkt);
    }
  

    //--------------------------------------------------
    public void printDistanceTable() {


	myGUI.println("Current table for " + myID +
		      "  at time " + sim.getClocktime());
	myGUI.println("Distancetable:");
	
	StringBuilder s = new StringBuilder( F.format("dist", 7) + " | " );

	for( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    s.append( F.format(i, 5) );
	myGUI.println( s.toString() );

	for( int i = 0; i < s.length(); ++i )
	    myGUI.print("-");
	myGUI.println();
	
	/* Print distance to neighbours */
	for( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    {
		if( i == myID )
		    continue;

		s = new StringBuilder("nbr" + F.format(i, 3) + " | ");
		for( int j = 0; j < RouterSimulator.NUM_NODES; ++j )
		    s.append( F.format(distanceVector[i][j], 5) );
		myGUI.println( s.toString() );
	    }

	/* Print our dest*/
	myGUI.println("\nOur distance vector and routes");

	s = new StringBuilder(F.format("dest", 7) + " | ");
	for( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    s.append( F.format(i,5) );
	myGUI.println( s.toString() );

	for( int i = 0; i < s.length(); ++i )
	    myGUI.print("-");
	myGUI.println();

	/* Print our cost */
	s = new StringBuilder(F.format("cost", 7) + " | ");
	for( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    s.append( F.format(minCosts[i],5) );
	myGUI.println( s.toString() );

	/* Print our route */
	/* Unicode infinity symbol: \u221e. */
	/* Might have to change when showing the lab, not sure if it works on all machines*/
	s = new StringBuilder(F.format("route", 7) + " | ");
	for( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    {
		if(minRoute[i] != RouterSimulator.INFINITY)
		    s.append( F.format(minRoute[i], 5) );
		else
		    s.append( F.format('-', 5) ); 
	    }
	myGUI.println( s.toString() );
	myGUI.println();
    }

    //--------------------------------------------------
    public void updateLinkCost(int dest, int newcost) 
    {
	//update the linkcosts
	costs[dest] = newcost;

	/* if a new mincosts is found update and send */
	if ( minCosts[dest] > newcost )
	    {
		minCosts[dest] = newcost;
		minRoute[dest] = dest;
		sendDistanceVector();
	    }
	else if (minRoute[dest] == dest)
	    {
		minCosts[dest] = newcost;
		sendDistanceVector();
	    }
		
    }

    /* Init DV. Set all entries to INFINITY */
    private void init_distanceVector()
    {
	for(int[] row : distanceVector)
	    Arrays.fill(row, RouterSimulator.INFINITY);
    }


    /* Update distance vector for this node */
    /* (Bellman-Ford algorithm) */
    private boolean updateDistancevector(int source, int[] recvcosts)
    {
	/* Put updated costs in a temporary distance vector */
	int[] updatedDV = new int[RouterSimulator.NUM_NODES];
	System.arraycopy(minCosts,0,updatedDV,0,RouterSimulator.NUM_NODES);


	/* for every node calculate a mincost and nexthop */
	for ( int dest = 0; dest < RouterSimulator.NUM_NODES; ++dest )
	    {
		/* skip calculation to myself */
		if ( dest == myID || dest == source ) /* should we add || dest == source not clear */
		    continue;
		find_min_path(dest,source,recvcosts);
	    }
	
	/* update the matrix for printfunction */
	System.arraycopy(updatedDV,0,distanceVector[myID],0,RouterSimulator.NUM_NODES);

	return !Arrays.equals(updatedDV,minCosts);
    }
    
    /* Find the minimal path to dest */
    /* Returns the ID of the router to where we should send in order to reach dest */
    private void find_min_path(int dest, int source, int[] recvcosts)
    {
	boolean myCostIsINF		= (minCosts[dest]	== RouterSimulator.INFINITY);
	boolean srcCostIsINF		= (recvcosts[dest]	== RouterSimulator.INFINITY);
	boolean minRouteIsSource	= (minRoute[dest]	== source);
	int newcost                     = recvcosts[dest] + minCosts[source];
	int mylinkcost                  = costs[dest];

	if (costs[dest] == RouterSimulator.INFINITY)
	    mylinkcost = RouterSimulator.INFINITY;
	
	if ( myCostIsINF || minRouteIsSource )
	      {
		if ( srcCostIsINF )
		    {
			minCosts[dest] = RouterSimulator.INFINITY;
			minRoute[dest] = source;
			return; 
		    }

		if ( newcost < RouterSimulator.INFINITY)
		    {
			if (newcost < mylinkcost)
			    {
				minCosts[dest] = newcost;
				minRoute[dest] = source;
			    }
			else
			    {
				minCosts[dest] = mylinkcost;
				minRoute[dest] = dest;
			    }
		    }
		return;
	    }

	
	if ( minCosts[dest] > newcost)
	    {
		if ( newcost < RouterSimulator.INFINITY && newcost < mylinkcost)
		    {
			minCosts[dest] = newcost;
			minRoute[dest] = minRoute[source];
		    }
		else
		    {
			minCosts[dest] = mylinkcost;
			minRoute[dest] = dest;
		    }
	    }
	
	return;
    }

    private void sendDistanceVector()
    {
	
		
	int[] tempDV = new int[RouterSimulator.NUM_NODES];

	/* Send the distance vector to all adjacent neighbours */
	for( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    {
		/* Send to -only- adjacent neighbours */
		if( i == myID || minCosts[i] == RouterSimulator.INFINITY )
		    continue;
			
		System.arraycopy(minCosts,0,tempDV,0,RouterSimulator.NUM_NODES);

		if(poisonedReverse)
		    {
			for (int j = 0; j < RouterSimulator.NUM_NODES; ++j)
			    {
				/* poison routes which's nexthop is the destination
				 * e.g. if A -> C goes VIA B (i) say A's cost to(C) is INF
				 * this way B won't try to route back through A */
				if (minRoute[j] == i )
				    tempDV[j] = RouterSimulator.INFINITY; 
			    }
		    }	
		/* Send the distance vector */
		RouterPacket pkt = new RouterPacket(myID, i, tempDV);
		sendUpdate(pkt);
	    }
    }
}
