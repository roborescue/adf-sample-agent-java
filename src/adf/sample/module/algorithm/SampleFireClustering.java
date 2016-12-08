package adf.sample.module.algorithm;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.DynamicClustering;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class SampleFireClustering extends DynamicClustering
{
    private int groupingDistance;
    List<List<StandardEntity>> clusterList = new LinkedList<>();

    public SampleFireClustering(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
    {
        super(ai, wi, si, moduleManager, developData);
        this.groupingDistance = developData.getInteger("adf.sample.module.algorithm.SampleFireClustering.groupingDistance", 30);
    }

    /**
     * calculation phase; update cluster
     * @return own instance for method chaining
     */
    @Override
    public Clustering calc()
    {
        for (EntityID changed : worldInfo.getChanged().getChangedEntities())
        {
            StandardEntity changedEntity = worldInfo.getEntity(changed);
            if (changedEntity.getStandardURN().equals(StandardEntityURN.BUILDING))
            { // changedEntity(cE) is a building
                Building changedBuilding = (Building)changedEntity;
                if (this.getClusterIndex(changedEntity) < 0)
                { // cE is not contained in cluster
                    if (isBurning(changedBuilding))
                    { // cE is burning building
                        ArrayList<EntityID> hostClusterPropertyEntityIDs = new ArrayList<>();

                        // search host cluster
                        for (List<StandardEntity> cluster : this.clusterList)
                        {
                            for (StandardEntity entity : cluster)
                            {
                                if (worldInfo.getDistance(entity, changedBuilding) <= groupingDistance)
                                {
                                    hostClusterPropertyEntityIDs.add(entity.getID());
                                    break;
                                }
                            }
                        }

                        if (hostClusterPropertyEntityIDs.size() == 0)
                        { // there is not host cluster : form new cluster
                            List<StandardEntity> cluster = new ArrayList<>();
                            clusterList.add(cluster);
                            cluster.add(changedBuilding);
                        }
                        else if (hostClusterPropertyEntityIDs.size() == 1)
                        { // there is one host cluster : add building to the cluster
                            int hostIndex = this.getClusterIndex(hostClusterPropertyEntityIDs.get(0));
                            clusterList.get(hostIndex).add(changedBuilding);
                        }
                        else
                        { // there are multiple host clusters : add building to the cluster & combine clusters
                            int hostIndex = this.getClusterIndex(hostClusterPropertyEntityIDs.get(0));
                            List<StandardEntity> hostCluster = clusterList.get(hostIndex);
                            hostCluster.add(changedBuilding);
                            for (int index = 1; index < hostClusterPropertyEntityIDs.size(); index++)
                            {
                                int tergetClusterIndex = this.getClusterIndex(hostClusterPropertyEntityIDs.get(index));
                                hostCluster.addAll(clusterList.get(tergetClusterIndex));
                                clusterList.remove(tergetClusterIndex);
                            }
                        }
                    }
                }
                else
                { // cE is contained in cluster
                    if (!(isBurning(changedBuilding)))
                    { // cE is not burning building
                        int hostClusterIndex = this.getClusterIndex(changedBuilding);
                        List<StandardEntity> hostCluster = clusterList.get(hostClusterIndex);

                        hostCluster.remove(changedBuilding);

                        if (hostCluster.isEmpty())
                        { // host cluster is empty
                            clusterList.remove(hostClusterIndex);
                        }
                        else
                        {
                            // update cluster
                            List<StandardEntity> relatedBuilding = new ArrayList<>();
                            relatedBuilding.addAll(hostCluster);
                            hostCluster.clear();

                            int clusterCount = 0;
                            while (!(relatedBuilding.isEmpty()))
                            {
                                if ((clusterCount++) > 0)
                                {
                                    List<StandardEntity> cluster = new ArrayList<>();
                                    clusterList.add(cluster);
                                    hostCluster = cluster;
                                }

                                List<StandardEntity> openedBuilding = new LinkedList<>();
                                openedBuilding.add(relatedBuilding.get(0));
                                hostCluster.add(relatedBuilding.get(0));
                                relatedBuilding.remove(0);

                                while (!(openedBuilding.isEmpty()))
                                {
                                    for (StandardEntity entity : relatedBuilding)
                                    {
                                        if (worldInfo.getDistance(openedBuilding.get(0), entity) <= groupingDistance)
                                        {
                                            openedBuilding.add(entity);
                                            hostCluster.add(entity);
                                        }
                                    }
                                    openedBuilding.remove(0);
                                    relatedBuilding.removeAll(openedBuilding);
                                }
                            }
                        }
                    }
                }
            }
        }
        return this;
    }

    @Override
    public Clustering updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() > 1) { return this; }

        this.calc(); // invoke calc()

        this.debugStdOut("Cluster : " + clusterList.size());

        return this;
    }

    @Override
    public Clustering precompute(PrecomputeData precomputeData)
    {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() > 1) { return this; }
        return this;
    }

    @Override
    public Clustering resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if(this.getCountResume() > 1) { return this; }
        return this;
    }

    @Override
    public Clustering preparate()
    {
        super.preparate();
        if(this.getCountPreparate() > 1) { return this; }
        return this;
    }

    @Override
    public int getClusterNumber()
    {
        return clusterList.size();
    }

    @Override
    public int getClusterIndex(StandardEntity standardEntity)
    {
        for (int index = 0; index < clusterList.size(); index++)
        {
            if (clusterList.get(index).contains(standardEntity))
            { return index; }
        }
        return -1;
    }

    @Override
    public int getClusterIndex(EntityID entityID)
    {
        return getClusterIndex(worldInfo.getEntity(entityID));
    }

    @Override
    public Collection<StandardEntity> getClusterEntities(int i)
    {
        return clusterList.get(i);
    }

    @Override
    public Collection<EntityID> getClusterEntityIDs(int i)
    {
        ArrayList<EntityID> list = new ArrayList<>();
        for (StandardEntity entity : getClusterEntities(i))
        { list.add(entity.getID()); }
        return list;
    }


    /**
     * classify burning building
     * @param building target building
     * @return is building burning
     */
    private boolean isBurning(Building building)
    {
        if (building.isFierynessDefined())
        {
            switch (building.getFieryness())
            {
                case 1: case 2: case 3:
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    /**
     * output text with class name to STDOUT when debug-mode.
     * @param text output text
     */
    private void debugStdOut(String text)
    {
        if (scenarioInfo.isDebugMode())
        { System.out.println("[" + this.getClass().getSimpleName() + "] " + text); }
    }
}