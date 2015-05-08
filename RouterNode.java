import javax.swing.*;        
import java.util.Arrays;
/* F.java has some issue ... tried using the java Formatter instead. */
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
    private boolean poisonedReverse = true;

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
	updateDistancevector(pkt.sourceid, pkt.mincost);
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
	
	/* Found this StringBuilder thing that's pretty neat //A */
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
		s.append( F.format(costs[i],5) );
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
	costs[dest] = newcost;
	updateDistancevector(myID,costs);
    }

    /* Init DV. Set all entries to INFINITY */
    private void init_distanceVector()
    {
	for(int[] row : distanceVector)
	    Arrays.fill(row, RouterSimulator.INFINITY);
    }


    /* Update distance vector for this node */
    /* (Bellman-Ford algorithm) */
    private void updateDistancevector(int source, int[] mincosts)
    {
	/* Put updated costs in a temporary distance vector */
	int[] updatedDV = new int[RouterSimulator.NUM_NODES];
	System.arraycopy(costs,0,updatedDV,0,RouterSimulator.NUM_NODES);

	for ( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    {
		if ( i == myID || i == source)
		    continue;
		find_min_path(i,source,mincosts);
	    }
	
	if(!Arrays.equals(updatedDV,costs))
	    {
		System.arraycopy(distanceVector[myID],0,updatedDV,0,RouterSimulator.NUM_NODES);
		sendDistanceVector();
	    }
    }
    
    /* Find the minimal path to dest */
    /* Returns the ID of the router to where we should send in order to reach dest */
    private void find_min_path(int dest, int source, int[] mincosts)
    {
	/* if we got the packet from source AND mincosts[dest] is not INFINITY there HAS to be a cost[dest]
	   != INFINTY via minroute[source] = mincosts[myID]+mincosts[dest].
	   
	   if mincost[dest] == INFINITY AND cost[dest] == INFINITY this code works
	*/
	bool myDest        = (minRoute[dest] == RouterSimulator.INFINITY);
	bool myToSource    = (minRoute[source] == RouterSimulator.INFINITY);
	bool costViaSource = (mincosts[dest] == RouterSimulator.INFINITY);
	
	int newcost        = mincosts[dest] + costs[source];

	if ( myDest )
	    newcost = mincosts[myID] + mincosts[dest];
	
	/* source is our current next hop to dest, our costs has to be updated with source's 
	   mincosts to dest regardless of shit 
	*/
	if ( source == minRoute[dest] )
	    {
		if ( costViaSource )
		    {
			cost[dest] = RouterSimulator.INFINITY;
			minRoute[dest] = RouterSimulator.INFINITY;
			return;
		    }
		else
		    costs[dest] = mincosts[myID] + mincosts[dest];
		
		return;
	    }

	if ( myToSource )
	    {
		minRoute[source] = source;
		costs[source] = mincosts[myID];
		return;
	    }

	if ( costs[dest] > newcost)
	    {
		costs[dest] = newcost;
		minRoute[dest] = source;
		return;
	    }
	return;
	/*	 
	int distance = costs[dest];
	int path;

	if( distance != RouterSimulator.INFINITY )
	    path = dest;
	else
	    path = RouterSimulator.INFINITY;

	for( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    {
		if( i == dest || i == myID )
		    continue;

		// Seems ok right now 
		if( costs[i] != RouterSimulator.INFINITY &&
		    distanceVector[i][dest] != RouterSimulator.INFINITY &&
		    distance > costs[i] + distanceVector[i][dest] )
		    {
			distance = costs[i] + distanceVector[i][dest];
			//System.out.println(distanceVector[i][dest]);
			//System.out.println(minRoute[dest]);
			path = i;
		    }
	    }
	return path;
	*/
    }

    private void sendDistanceVector()
    {
	/* Send the distance vector to all adjacent neighbours */
	for( int i = 0; i < RouterSimulator.NUM_NODES; ++i )
	    {
		/* Send to -only- adjacent neighbours */
		if( i == myID || costs[i] == RouterSimulator.INFINITY )
		    continue;

		int[] tempDV = new int[RouterSimulator.NUM_NODES];
		for( int j = 0; j < RouterSimulator.NUM_NODES; ++j )
		    {
			if( poisonedReverse && i == minRoute[j] )
			    tempDV[j] = RouterSimulator.INFINITY;
			else
			    tempDV[j] = costs[j];
			//System.out.println(distanceVector[myID][j]);
			//System.out.println(costs[j]);			     
		    }
		
		/* Send the distance vector */
		RouterPacket pkt = new RouterPacket(myID, i, tempDV);
		sendUpdate(pkt);
	    }
    }
}
