package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.complex.AmbulanceTargetAllocator;
import rescuecore2.worldmodel.EntityID;

import java.util.HashMap;
import java.util.Map;

public class SampleDecentralizedAmbulanceTargetAllocator extends AmbulanceTargetAllocator
{
    public SampleDecentralizedAmbulanceTargetAllocator(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
    {
        super(ai, wi, si, moduleManager, developData);
    }

    @Override
    public AmbulanceTargetAllocator resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2)
        {
            return this;
        }
        return this;
    }

    @Override
    public AmbulanceTargetAllocator preparate()
    {
        super.preparate();
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        return this;
    }

    @Override
    public Map<EntityID, EntityID> getResult()
    {
        return new HashMap<>();
    }

    @Override
    public AmbulanceTargetAllocator calc()
    {
        return this;
    }

    @Override
    public AmbulanceTargetAllocator updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2)
        {
            return this;
        }
        return this;
    }

}
