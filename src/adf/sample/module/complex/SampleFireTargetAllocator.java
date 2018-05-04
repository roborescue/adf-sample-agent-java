package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.centralized.CommandFire;
import adf.agent.communication.standard.bundle.centralized.MessageReport;
import adf.agent.communication.standard.bundle.information.MessageBuilding;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.FireTargetAllocator;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class SampleFireTargetAllocator extends FireTargetAllocator
{

    private Collection<EntityID> priorityBuildings;
    private Collection<EntityID> targetBuildings;

    private Map<EntityID, FireBrigadeInfo> agentInfoMap;

    private int maxWater;
    private int maxPower;

    public SampleFireTargetAllocator(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
    {
        super(ai, wi, si, moduleManager, developData);
        this.priorityBuildings = new HashSet<>();
        this.targetBuildings = new HashSet<>();
        this.agentInfoMap = new HashMap<>();
        this.maxWater = si.getFireTankMaximum();
        this.maxPower = si.getFireExtinguishMaxSum();
    }

    @Override
    public FireTargetAllocator resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2)
        {
            return this;
        }
        for (EntityID id : this.worldInfo.getEntityIDsOfType(StandardEntityURN.FIRE_BRIGADE))
        {
            this.agentInfoMap.put(id, new FireBrigadeInfo(id));
        }
        return this;
    }

    @Override
    public FireTargetAllocator preparate()
    {
        super.preparate();
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        for (EntityID id : this.worldInfo.getEntityIDsOfType(StandardEntityURN.FIRE_BRIGADE))
        {
            this.agentInfoMap.put(id, new FireBrigadeInfo(id));
        }
        return this;
    }

    @Override
    public Map<EntityID, EntityID> getResult()
    {
        return this.convert(this.agentInfoMap);
    }

    @Override
    public FireTargetAllocator calc()
    {
        int currentTime = this.agentInfo.getTime();
        List<StandardEntity> agents = this.getActionAgents(this.agentInfoMap);
        Collection<EntityID> removes = new ArrayList<>();
        for (EntityID target : this.priorityBuildings)
        {
            if (agents.size() > 0)
            {
                StandardEntity targetEntity = this.worldInfo.getEntity(target);
                if (targetEntity != null)
                {
                    agents.sort(new DistanceSorter(this.worldInfo, targetEntity));
                    StandardEntity result = agents.get(0);
                    agents.remove(0);
                    FireBrigadeInfo info = this.agentInfoMap.get(result.getID());
                    if (info != null)
                    {
                        info.canNewAction = false;
                        info.target = target;
                        info.commandTime = currentTime;
                        this.agentInfoMap.put(result.getID(), info);
                        removes.add(target);
                    }
                }
            }
        }
        this.priorityBuildings.removeAll(removes);
        removes.clear();
        for (EntityID target : this.targetBuildings)
        {
            if (agents.size() > 0)
            {
                StandardEntity targetEntity = this.worldInfo.getEntity(target);
                if (targetEntity != null)
                {
                    agents.sort(new DistanceSorter(this.worldInfo, targetEntity));
                    StandardEntity result = agents.get(0);
                    agents.remove(0);
                    FireBrigadeInfo info = this.agentInfoMap.get(result.getID());
                    if (info != null)
                    {
                        info.canNewAction = false;
                        info.target = target;
                        info.commandTime = currentTime;
                        this.agentInfoMap.put(result.getID(), info);
                        removes.add(target);
                    }
                }
            }
        }
        this.targetBuildings.removeAll(removes);
        return this;
    }

    @Override
    public FireTargetAllocator updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2)
        {
            return this;
        }
        int currentTime = this.agentInfo.getTime();
        for (CommunicationMessage message : messageManager.getReceivedMessageList(MessageBuilding.class))
        {
            MessageBuilding mb = (MessageBuilding) message;
            Building building = MessageUtil.reflectMessage(this.worldInfo, mb);
            if (building.isOnFire())
            {
                this.targetBuildings.add(building.getID());
            }
            else
            {
                this.priorityBuildings.remove(mb.getBuildingID());
                this.targetBuildings.remove(mb.getBuildingID());
            }
        }
        for (CommunicationMessage message : messageManager.getReceivedMessageList(MessageFireBrigade.class))
        {
            MessageFireBrigade mfb = (MessageFireBrigade) message;
            MessageUtil.reflectMessage(this.worldInfo, mfb);
            FireBrigadeInfo info = this.agentInfoMap.get(mfb.getAgentID());
            if (info == null)
            {
                info = new FireBrigadeInfo(mfb.getAgentID());
            }
            if (currentTime >= info.commandTime + 2)
            {
                this.agentInfoMap.put(mfb.getAgentID(), this.update(info, mfb));
            }
        }
        for (CommunicationMessage message : messageManager.getReceivedMessageList(CommandFire.class))
        {
            CommandFire command = (CommandFire) message;
            if (command.getAction() == CommandFire.ACTION_EXTINGUISH && command.isBroadcast())
            {
                this.priorityBuildings.add(command.getTargetID());
                this.targetBuildings.add(command.getTargetID());
            }
        }
        for (CommunicationMessage message : messageManager.getReceivedMessageList(MessageReport.class))
        {
            MessageReport report = (MessageReport) message;
            FireBrigadeInfo info = this.agentInfoMap.get(report.getSenderID());
            if (info != null && report.isDone())
            {
                info.canNewAction = true;
                this.priorityBuildings.remove(info.target);
                this.targetBuildings.remove(info.target);
                info.target = null;
                this.agentInfoMap.put(report.getSenderID(), info);
            }
        }
        return this;
    }

    private Map<EntityID, EntityID> convert(Map<EntityID, FireBrigadeInfo> infoMap)
    {
        Map<EntityID, EntityID> result = new HashMap<>();
        for (EntityID id : infoMap.keySet())
        {
            FireBrigadeInfo info = infoMap.get(id);
            if (info != null && info.target != null)
            {
                result.put(id, info.target);
            }
        }
        return result;
    }

    private List<StandardEntity> getActionAgents(Map<EntityID, FireBrigadeInfo> infoMap)
    {
        List<StandardEntity> result = new ArrayList<>();
        for (StandardEntity entity : this.worldInfo.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE))
        {
            FireBrigadeInfo info = infoMap.get(entity.getID());
            if (info != null && info.canNewAction && ((FireBrigade) entity).isPositionDefined())
            {
                result.add(entity);
            }
        }
        return result;
    }

    private FireBrigadeInfo update(FireBrigadeInfo info, MessageFireBrigade message)
    {
        if (message.isBuriednessDefined() && message.getBuriedness() > 0)
        {
            info.canNewAction = false;
            if (info.target != null)
            {
                this.targetBuildings.add(info.target);
                info.target = null;
            }
            return info;
        }
        if (message.getAction() == MessageFireBrigade.ACTION_REST)
        {
            info.canNewAction = true;
            if (info.target != null)
            {
                this.targetBuildings.add(info.target);
                info.target = null;
            }
        }
        else if (message.getAction() == MessageFireBrigade.ACTION_REFILL)
        {
            info.canNewAction = (message.getWater() + this.maxPower >= this.maxWater);
            if (info.target != null)
            {
                this.targetBuildings.add(info.target);
                info.target = null;
            }
        }
        else if (message.getAction() == MessageFireBrigade.ACTION_MOVE)
        {
            if (message.getTargetID() != null)
            {
                StandardEntity entity = this.worldInfo.getEntity(message.getTargetID());
                if (entity != null && entity instanceof Area)
                {
                    if (info.target != null)
                    {
                        StandardEntity targetEntity = this.worldInfo.getEntity(info.target);
                        if (targetEntity != null && targetEntity instanceof Area)
                        {
                            if (message.getTargetID().getValue() == info.target.getValue())
                            {
                                info.canNewAction = false;
                            }
                            else
                            {
                                info.canNewAction = true;
                                this.targetBuildings.add(info.target);
                                info.target = null;
                            }
                        }
                        else
                        {
                            info.canNewAction = true;
                            info.target = null;
                        }
                    }
                    else
                    {
                        info.canNewAction = true;
                    }
                }
                else
                {
                    info.canNewAction = true;
                    if (info.target != null)
                    {
                        this.targetBuildings.add(info.target);
                        info.target = null;
                    }
                }
            }
            else
            {
                info.canNewAction = true;
                if (info.target != null)
                {
                    this.targetBuildings.add(info.target);
                    info.target = null;
                }
            }
        }
        else if (message.getAction() == MessageFireBrigade.ACTION_EXTINGUISH)
        {
            info.canNewAction = true;
            info.target = null;
            this.priorityBuildings.remove(message.getTargetID());
            this.targetBuildings.remove(message.getTargetID());
        }
        return info;
    }

    private class FireBrigadeInfo
    {
        @SuppressWarnings("unused")
        EntityID agentID;
        EntityID target;
        boolean canNewAction;
        int commandTime;

        FireBrigadeInfo(EntityID id)
        {
            agentID = id;
            target = null;
            canNewAction = true;
            commandTime = -1;
        }
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
