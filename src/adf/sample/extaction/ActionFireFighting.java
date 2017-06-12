package adf.sample.extaction;


import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.action.fire.ActionRefill;
import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.HYDRANT;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class ActionFireFighting extends ExtAction
{
    private PathPlanning pathPlanning;

    private int maxExtinguishDistance;
    private int maxExtinguishPower;
    private int thresholdRest;
    private int kernelTime;
    private int refillCompleted;
    private int refillRequest;
    private boolean refillFlag;

    private EntityID target;

    public ActionFireFighting(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData)
    {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.maxExtinguishDistance = scenarioInfo.getFireExtinguishMaxDistance();
        this.maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();
        this.thresholdRest = developData.getInteger("ActionFireFighting.rest", 100);
        int maxWater = scenarioInfo.getFireTankMaximum();
        this.refillCompleted = (maxWater / 10) * developData.getInteger("ActionFireFighting.refill.completed", 10);
        this.refillRequest = this.maxExtinguishPower * developData.getInteger("ActionFireFighting.refill.request", 1);
        this.refillFlag = false;

        this.target = null;

        switch (scenarioInfo.getMode())
        {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("ActionFireFighting.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("ActionFireFighting.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("ActionFireFighting.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
        }
    }

    @Override
    public ExtAction precompute(PrecomputeData precomputeData)
    {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        try
        {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        }
        catch (NoSuchConfigOptionException e)
        {
            this.kernelTime = -1;
        }
        return this;
    }

    @Override
    public ExtAction resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2)
        {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        try
        {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        }
        catch (NoSuchConfigOptionException e)
        {
            this.kernelTime = -1;
        }
        return this;
    }

    @Override
    public ExtAction preparate()
    {
        super.preparate();
        if (this.getCountPreparate() >= 2)
        {
            return this;
        }
        this.pathPlanning.preparate();
        try
        {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        }
        catch (NoSuchConfigOptionException e)
        {
            this.kernelTime = -1;
        }
        return this;
    }

    @Override
    public ExtAction updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2)
        {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        return this;
    }

    @Override
    public ExtAction setTarget(EntityID target)
    {
        this.target = null;
        if (target != null)
        {
            StandardEntity entity = this.worldInfo.getEntity(target);
            if (entity instanceof Building)
            {
                this.target = target;
            }
        }
        return this;
    }

    @Override
    public ExtAction calc()
    {
        this.result = null;
        FireBrigade agent = (FireBrigade) this.agentInfo.me();

        this.refillFlag = this.needRefill(agent, this.refillFlag);
        if (this.refillFlag)
        {
            this.result = this.calcRefill(agent, this.pathPlanning, this.target);
            if (this.result != null)
            {
                return this;
            }
        }

        if (this.needRest(agent))
        {
            this.result = this.calcRefugeAction(agent, this.pathPlanning, this.target, false);
            if (this.result != null)
            {
                return this;
            }
        }

        if (this.target == null)
        {
            return this;
        }
        this.result = this.calcExtinguish(agent, this.pathPlanning, this.target);
        return this;
    }

    private Action calcExtinguish(FireBrigade agent, PathPlanning pathPlanning, EntityID target)
    {
        EntityID agentPosition = agent.getPosition();
        StandardEntity positionEntity = Objects.requireNonNull(this.worldInfo.getPosition(agent));
        if (StandardEntityURN.REFUGE == positionEntity.getStandardURN())
        {
            Action action = this.getMoveAction(pathPlanning, agentPosition, target);
            if (action != null)
            {
                return action;
            }
        }

        List<StandardEntity> neighbourBuilding = new ArrayList<>();
        StandardEntity entity = this.worldInfo.getEntity(target);
        if (entity instanceof Building)
        {
            if (this.worldInfo.getDistance(positionEntity, entity) < this.maxExtinguishDistance)
            {
                neighbourBuilding.add(entity);
            }
        }

        if (neighbourBuilding.size() > 0)
        {
            neighbourBuilding.sort(new DistanceSorter(this.worldInfo, agent));
            return new ActionExtinguish(neighbourBuilding.get(0).getID(), this.maxExtinguishPower);
        }
        return this.getMoveAction(pathPlanning, agentPosition, target);
    }

    private Action getMoveAction(PathPlanning pathPlanning, EntityID from, EntityID target)
    {
        pathPlanning.setFrom(from);
        pathPlanning.setDestination(target);
        List<EntityID> path = pathPlanning.calc().getResult();
        if (path != null && path.size() > 0)
        {
            StandardEntity entity = this.worldInfo.getEntity(path.get(path.size() - 1));
            if (entity instanceof Building)
            {
                if (entity.getStandardURN() != StandardEntityURN.REFUGE)
                {
                    path.remove(path.size() - 1);
                }
            }
            return new ActionMove(path);
        }
        return null;
    }

    private boolean needRefill(FireBrigade agent, boolean refillFlag)
    {
        if (refillFlag)
        {
            StandardEntityURN positionURN = Objects.requireNonNull(this.worldInfo.getPosition(agent)).getStandardURN();
            return !(positionURN == REFUGE || positionURN == HYDRANT) || agent.getWater() < this.refillCompleted;
        }
        return agent.getWater() <= this.refillRequest;
    }

    private boolean needRest(Human agent)
    {
        int hp = agent.getHP();
        int damage = agent.getDamage();
        if (hp == 0 || damage == 0)
        {
            return false;
        }
        int activeTime = (hp / damage) + ((hp % damage) != 0 ? 1 : 0);
        if (this.kernelTime == -1)
        {
            try
            {
                this.kernelTime = this.scenarioInfo.getKernelTimesteps();
            }
            catch (NoSuchConfigOptionException e)
            {
                this.kernelTime = -1;
            }
        }
        return damage >= this.thresholdRest || (activeTime + this.agentInfo.getTime()) < this.kernelTime;
    }

    private Action calcRefill(FireBrigade agent, PathPlanning pathPlanning, EntityID target)
    {
        StandardEntityURN positionURN = Objects.requireNonNull(this.worldInfo.getPosition(agent)).getStandardURN();
        if (positionURN == REFUGE)
        {
            return new ActionRefill();
        }
        Action action = this.calcRefugeAction(agent, pathPlanning, target, true);
        if (action != null)
        {
            return action;
        }
        action = this.calcHydrantAction(agent, pathPlanning, target);
        if (action != null)
        {
            if (positionURN == HYDRANT && action.getClass().equals(ActionMove.class))
            {
                pathPlanning.setFrom(agent.getPosition());
                pathPlanning.setDestination(target);
                double currentDistance = pathPlanning.calc().getDistance();
                List<EntityID> path = ((ActionMove) action).getPath();
                pathPlanning.setFrom(path.get(path.size() - 1));
                pathPlanning.setDestination(target);
                double newHydrantDistance = pathPlanning.calc().getDistance();
                if (currentDistance <= newHydrantDistance)
                {
                    return new ActionRefill();
                }
            }
            return action;
        }
        return null;
    }

    private Action calcRefugeAction(Human human, PathPlanning pathPlanning, EntityID target, boolean isRefill)
    {
        return this.calcSupplyAction(
                human,
                pathPlanning,
                this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE),
                target,
                isRefill
        );
    }

    private Action calcHydrantAction(Human human, PathPlanning pathPlanning, EntityID target)
    {
        Collection<EntityID> hydrants = this.worldInfo.getEntityIDsOfType(HYDRANT);
        hydrants.remove(human.getPosition());
        return this.calcSupplyAction(
                human,
                pathPlanning,
                hydrants,
                target,
                true
        );
    }

    private Action calcSupplyAction(Human human, PathPlanning pathPlanning, Collection<EntityID> supplyPositions, EntityID target, boolean isRefill)
    {
        EntityID position = human.getPosition();
        int size = supplyPositions.size();
        if (supplyPositions.contains(position))
        {
            return isRefill ? new ActionRefill() : new ActionRest();
        }
        List<EntityID> firstResult = null;
        while (supplyPositions.size() > 0)
        {
            pathPlanning.setFrom(position);
            pathPlanning.setDestination(supplyPositions);
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null && path.size() > 0)
            {
                if (firstResult == null)
                {
                    firstResult = new ArrayList<>(path);
                    if (target == null)
                    {
                        break;
                    }
                }
                EntityID supplyPositionID = path.get(path.size() - 1);
                pathPlanning.setFrom(supplyPositionID);
                pathPlanning.setDestination(target);
                List<EntityID> fromRefugeToTarget = pathPlanning.calc().getResult();
                if (fromRefugeToTarget != null && fromRefugeToTarget.size() > 0)
                {
                    return new ActionMove(path);
                }
                supplyPositions.remove(supplyPositionID);
                //remove failed
                if (size == supplyPositions.size())
                {
                    break;
                }
                size = supplyPositions.size();
            }
            else
            {
                break;
            }
        }
        return firstResult != null ? new ActionMove(firstResult) : null;
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

