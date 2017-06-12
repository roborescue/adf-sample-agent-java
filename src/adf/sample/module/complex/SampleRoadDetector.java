package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.communication.standard.bundle.information.MessageRoad;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.RoadDetector;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleRoadDetector extends RoadDetector
{
    private Set<EntityID> targetAreas;
    private Set<EntityID> priorityRoads;

    private PathPlanning pathPlanning;

    private EntityID result;

    public SampleRoadDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
    {
        super(ai, wi, si, moduleManager, developData);
        switch (scenarioInfo.getMode())
        {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
        }
        registerModule(this.pathPlanning);
        this.result = null;
    }

    @Override
    public RoadDetector calc()
    {
        if (this.result == null)
        {
            EntityID positionID = this.agentInfo.getPosition();
            if (this.targetAreas.contains(positionID))
            {
                this.result = positionID;
                return this;
            }
            List<EntityID> removeList = new ArrayList<>(this.priorityRoads.size());
            for (EntityID id : this.priorityRoads)
            {
                if (!this.targetAreas.contains(id))
                {
                    removeList.add(id);
                }
            }
            this.priorityRoads.removeAll(removeList);
            if (this.priorityRoads.size() > 0)
            {
                this.pathPlanning.setFrom(positionID);
                this.pathPlanning.setDestination(this.targetAreas);
                List<EntityID> path = this.pathPlanning.calc().getResult();
                if (path != null && path.size() > 0)
                {
                    this.result = path.get(path.size() - 1);
                }
                return this;
            }


            this.pathPlanning.setFrom(positionID);
            this.pathPlanning.setDestination(this.targetAreas);
            List<EntityID> path = this.pathPlanning.calc().getResult();
            if (path != null && path.size() > 0)
            {
                this.result = path.get(path.size() - 1);
            }
        }
        return this;
    }

    @Override
    public EntityID getTarget()
    {
        return this.result;
    }

    @Override
    public RoadDetector precompute(PrecomputeData precomputeData)
    {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        return this;
    }

    @Override
    public RoadDetector resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2)
        {
            return this;
        }
        this.targetAreas = new HashSet<>();
        for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE, BUILDING, GAS_STATION))
        {
            for (EntityID id : ((Building) e).getNeighbours())
            {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if (neighbour instanceof Road)
                {
                    this.targetAreas.add(id);
                }
            }
        }
        this.priorityRoads = new HashSet<>();
        for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE))
        {
            for (EntityID id : ((Building) e).getNeighbours())
            {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if (neighbour instanceof Road)
                {
                    this.priorityRoads.add(id);
                }
            }
        }
        return this;
    }

    @Override
    public RoadDetector preparate()
    {
        super.preparate();
        if (this.getCountPreparate() >= 2)
        {
            return this;
        }
        this.targetAreas = new HashSet<>();
        for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE, BUILDING, GAS_STATION))
        {
            for (EntityID id : ((Building) e).getNeighbours())
            {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if (neighbour instanceof Road)
                {
                    this.targetAreas.add(id);
                }
            }
        }
        this.priorityRoads = new HashSet<>();
        for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE))
        {
            for (EntityID id : ((Building) e).getNeighbours())
            {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if (neighbour instanceof Road)
                {
                    this.priorityRoads.add(id);
                }
            }
        }
        return this;
    }

    @Override
    public RoadDetector updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2)
        {
            return this;
        }
        if (this.result != null)
        {
            if (this.agentInfo.getPosition().equals(this.result))
            {
                StandardEntity entity = this.worldInfo.getEntity(this.result);
                if (entity instanceof Building)
                {
                    this.result = null;
                }
                else if (entity instanceof Road)
                {
                    Road road = (Road) entity;
                    if (!road.isBlockadesDefined() || road.getBlockades().isEmpty())
                    {
                        this.targetAreas.remove(this.result);
                        this.result = null;
                    }
                }
            }
        }
        Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
        for (CommunicationMessage message : messageManager.getReceivedMessageList())
        {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if (messageClass == MessageAmbulanceTeam.class)
            {
                this.reflectMessage((MessageAmbulanceTeam) message);
            }
            else if (messageClass == MessageFireBrigade.class)
            {
                this.reflectMessage((MessageFireBrigade) message);
            }
            else if (messageClass == MessageRoad.class)
            {
                this.reflectMessage((MessageRoad) message, changedEntities);
            }
            else if (messageClass == MessagePoliceForce.class)
            {
                this.reflectMessage((MessagePoliceForce) message);
            }
            else if (messageClass == CommandPolice.class)
            {
                this.reflectMessage((CommandPolice) message);
            }
        }
        for (EntityID id : this.worldInfo.getChanged().getChangedEntities())
        {
            StandardEntity entity = this.worldInfo.getEntity(id);
            if (entity instanceof Road)
            {
                Road road = (Road) entity;
                if (!road.isBlockadesDefined() || road.getBlockades().isEmpty())
                {
                    this.targetAreas.remove(id);
                }
            }
        }
        return this;
    }

    private void reflectMessage(MessageRoad messageRoad, Collection<EntityID> changedEntities)
    {
        if (messageRoad.isBlockadeDefined() && !changedEntities.contains(messageRoad.getBlockadeID()))
        {
            MessageUtil.reflectMessage(this.worldInfo, messageRoad);
        }
        if (messageRoad.isPassable())
        {
            this.targetAreas.remove(messageRoad.getRoadID());
        }
    }

    private void reflectMessage(MessageAmbulanceTeam messageAmbulanceTeam)
    {
        if (messageAmbulanceTeam.getPosition() == null)
        {
            return;
        }
        if (messageAmbulanceTeam.getAction() == MessageAmbulanceTeam.ACTION_RESCUE)
        {
            StandardEntity position = this.worldInfo.getEntity(messageAmbulanceTeam.getPosition());
            if (position != null && position instanceof Building)
            {
                this.targetAreas.removeAll(((Building) position).getNeighbours());
            }
        }
        else if (messageAmbulanceTeam.getAction() == MessageAmbulanceTeam.ACTION_LOAD)
        {
            StandardEntity position = this.worldInfo.getEntity(messageAmbulanceTeam.getPosition());
            if (position != null && position instanceof Building)
            {
                this.targetAreas.removeAll(((Building) position).getNeighbours());
            }
        }
        else if (messageAmbulanceTeam.getAction() == MessageAmbulanceTeam.ACTION_MOVE)
        {
            if (messageAmbulanceTeam.getTargetID() == null)
            {
                return;
            }
            StandardEntity target = this.worldInfo.getEntity(messageAmbulanceTeam.getTargetID());
            if (target instanceof Building)
            {
                for (EntityID id : ((Building) target).getNeighbours())
                {
                    StandardEntity neighbour = this.worldInfo.getEntity(id);
                    if (neighbour instanceof Road)
                    {
                        this.priorityRoads.add(id);
                    }
                }
            }
            else if (target instanceof Human)
            {
                Human human = (Human) target;
                if (human.isPositionDefined())
                {
                    StandardEntity position = this.worldInfo.getPosition(human);
                    if (position instanceof Building)
                    {
                        for (EntityID id : ((Building) position).getNeighbours())
                        {
                            StandardEntity neighbour = this.worldInfo.getEntity(id);
                            if (neighbour instanceof Road)
                            {
                                this.priorityRoads.add(id);
                            }
                        }
                    }
                }
            }
        }
    }

    private void reflectMessage(MessageFireBrigade messageFireBrigade)
    {
        if (messageFireBrigade.getTargetID() == null)
        {
            return;
        }
        if (messageFireBrigade.getAction() == MessageFireBrigade.ACTION_REFILL)
        {
            StandardEntity target = this.worldInfo.getEntity(messageFireBrigade.getTargetID());
            if (target instanceof Building)
            {
                for (EntityID id : ((Building) target).getNeighbours())
                {
                    StandardEntity neighbour = this.worldInfo.getEntity(id);
                    if (neighbour instanceof Road)
                    {
                        this.priorityRoads.add(id);
                    }
                }
            }
            else if (target.getStandardURN() == HYDRANT)
            {
                this.priorityRoads.add(target.getID());
                this.targetAreas.add(target.getID());
            }
        }
    }

    private void reflectMessage(MessagePoliceForce messagePoliceForce)
    {
        if (messagePoliceForce.getAction() == MessagePoliceForce.ACTION_CLEAR)
        {
            if (messagePoliceForce.getAgentID().getValue() != this.agentInfo.getID().getValue())
            {
                if (messagePoliceForce.isTargetDefined())
                {
                    EntityID targetID = messagePoliceForce.getTargetID();
                    if (targetID == null)
                    {
                        return;
                    }
                    StandardEntity entity = this.worldInfo.getEntity(targetID);
                    if (entity == null)
                    {
                        return;
                    }

                    if (entity instanceof Area)
                    {
                        this.targetAreas.remove(targetID);
                        if (this.result != null && this.result.getValue() == targetID.getValue())
                        {
                            if (this.agentInfo.getID().getValue() < messagePoliceForce.getAgentID().getValue())
                            {
                                this.result = null;
                            }
                        }
                    }
                    else if (entity.getStandardURN() == BLOCKADE)
                    {
                        EntityID position = ((Blockade) entity).getPosition();
                        this.targetAreas.remove(position);
                        if (this.result != null && this.result.getValue() == position.getValue())
                        {
                            if (this.agentInfo.getID().getValue() < messagePoliceForce.getAgentID().getValue())
                            {
                                this.result = null;
                            }
                        }
                    }

                }
            }
        }
    }

    private void reflectMessage(CommandPolice commandPolice)
    {
        boolean flag = false;
        if (commandPolice.isToIDDefined() && this.agentInfo.getID().getValue() == commandPolice.getToID().getValue())
        {
            flag = true;
        }
        else if (commandPolice.isBroadcast())
        {
            flag = true;
        }
        if (flag && commandPolice.getAction() == CommandPolice.ACTION_CLEAR)
        {
            if (commandPolice.getTargetID() == null)
            {
                return;
            }
            StandardEntity target = this.worldInfo.getEntity(commandPolice.getTargetID());
            if (target instanceof Area)
            {
                this.priorityRoads.add(target.getID());
                this.targetAreas.add(target.getID());
            }
            else if (target.getStandardURN() == BLOCKADE)
            {
                Blockade blockade = (Blockade) target;
                if (blockade.isPositionDefined())
                {
                    this.priorityRoads.add(blockade.getPosition());
                    this.targetAreas.add(blockade.getPosition());
                }
            }
        }
    }
}
