package adf.sample.module.complex;


import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.BuildingDetector;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;
import java.util.*;

public class SampleBuildingDetector extends BuildingDetector
{
    private EntityID result;

    private Clustering clustering;

    public SampleBuildingDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
    {
        super(ai, wi, si, moduleManager, developData);
        switch (si.getMode())
        {
            case PRECOMPUTATION_PHASE:
                this.clustering = moduleManager.getModule("SampleBuildingDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case PRECOMPUTED:
                this.clustering = moduleManager.getModule("SampleBuildingDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case NON_PRECOMPUTE:
                this.clustering = moduleManager.getModule("SampleBuildingDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
        }
        registerModule(this.clustering);
    }


    @Override
    public BuildingDetector updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2)
        {
            return this;
        }

        return this;
    }

    @Override
    public BuildingDetector calc()
    {
        this.result = this.calcTargetInCluster();
        if (this.result == null)
        {
            this.result = this.calcTargetInWorld();
        }
        return this;
    }

    private EntityID calcTargetInCluster()
    {
        int clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
        Collection<StandardEntity> elements = this.clustering.getClusterEntities(clusterIndex);
        if (elements == null || elements.isEmpty())
        {
            return null;
        }
        StandardEntity me = this.agentInfo.me();
        List<StandardEntity> agents = new ArrayList<>(this.worldInfo.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE));
        Set<StandardEntity> fireBuildings = new HashSet<>();
        for (StandardEntity entity : elements)
        {
            if (entity instanceof Building && ((Building) entity).isOnFire())
            {
                fireBuildings.add(entity);
            }
        }
        for (StandardEntity entity : fireBuildings)
        {
            if (agents.isEmpty())
            {
                break;
            }
            else if (agents.size() == 1)
            {
                if (agents.get(0).getID().getValue() == me.getID().getValue())
                {
                    return entity.getID();
                }
                break;
            }
            agents.sort(new DistanceSorter(this.worldInfo, entity));
            StandardEntity a0 = agents.get(0);
            StandardEntity a1 = agents.get(1);

            if (me.getID().getValue() == a0.getID().getValue() || me.getID().getValue() == a1.getID().getValue())
            {
                return entity.getID();
            }
            else
            {
                agents.remove(a0);
                agents.remove(a1);
            }
        }
        return null;
    }

    private EntityID calcTargetInWorld()
    {
        Collection<StandardEntity> entities = this.worldInfo.getEntitiesOfType(
                StandardEntityURN.BUILDING,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        );
        StandardEntity me = this.agentInfo.me();
        List<StandardEntity> agents = new ArrayList<>(worldInfo.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE));
        Set<StandardEntity> fireBuildings = new HashSet<>();
        for (StandardEntity entity : entities)
        {
            if (((Building) entity).isOnFire())
            {
                fireBuildings.add(entity);
            }
        }
        for (StandardEntity entity : fireBuildings)
        {
            if (agents.isEmpty())
            {
                break;
            }
            else if (agents.size() == 1)
            {
                if (agents.get(0).getID().getValue() == me.getID().getValue())
                {
                    return entity.getID();
                }
                break;
            }
            agents.sort(new DistanceSorter(this.worldInfo, entity));
            StandardEntity a0 = agents.get(0);
            StandardEntity a1 = agents.get(1);

            if (me.getID().getValue() == a0.getID().getValue() || me.getID().getValue() == a1.getID().getValue())
            {
                return entity.getID();
            }
            else
            {
                agents.remove(a0);
                agents.remove(a1);
            }
        }
        return null;
    }

    @Override
    public EntityID getTarget()
    {
        return this.result;
    }

    @Override
    public BuildingDetector precompute(PrecomputeData precomputeData)
    {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        return this;
    }

    @Override
    public BuildingDetector resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        return this;
    }

    @Override
    public BuildingDetector preparate()
    {
        super.preparate();
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        return this;
    }

    @SuppressWarnings("unused")
    private double getAngle(Vector2D v1, Vector2D v2)
    {
        double flag = (v1.getX() * v2.getY()) - (v1.getY() * v2.getX());
        double angle = Math.acos(((v1.getX() * v2.getX()) + (v1.getY() * v2.getY())) / (v1.getLength() * v2.getLength()));
        if (flag > 0)
        {
            return angle;
        }
        if (flag < 0)
        {
            return -1 * angle;
        }
        return 0.0D;
    }

    private class DistanceSorter implements Comparator<StandardEntity>
    {
        private StandardEntity reference;
        private WorldInfo worldInfo;

        DistanceSorter(WorldInfo wi, StandardEntity reference)
        {
            this.reference = reference;
            this.worldInfo = wi;
        }

        public int compare(StandardEntity a, StandardEntity b)
        {
            int d1 = this.worldInfo.getDistance(this.reference, a);
            int d2 = this.worldInfo.getDistance(this.reference, b);
            return d1 - d2;
        }
    }
}
