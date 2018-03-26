package adf.sample.tactics;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.debug.WorldViewLauncher;
import adf.component.centralized.CommandPicker;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.TargetAllocator;
import adf.component.tactics.TacticsPoliceOffice;
import rescuecore2.worldmodel.EntityID;

import java.util.Map;

public class SampleTacticsPoliceOffice extends TacticsPoliceOffice
{
    private TargetAllocator allocator;
    private CommandPicker picker;
	private Boolean isVisualDebug;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData)
    {
        messageManager.setChannelSubscriber(moduleManager.getChannelSubscriber("MessageManager.CenterChannelSubscriber", "adf.component.communication.ChannelSubscriber"));
        messageManager.setMessageCoordinator(moduleManager.getMessageCoordinator("MessageManager.CenterMessageCoordinator", "adf.agent.communication.standard.bundle.StandardMessageCoordinator"));

        switch  (scenarioInfo.getMode())
        {
            case PRECOMPUTATION_PHASE:
            case PRECOMPUTED:
                this.allocator = moduleManager.getModule(
                        "TacticsPoliceOffice.TargetAllocator",
                        "adf.sample.module.complex.SamplePoliceTargetAllocator");
                this.picker = moduleManager.getCommandPicker(
                        "TacticsPoliceOffice.CommandPicker",
                        "adf.sample.centralized.CommandPickerPolice");
                break;
            case NON_PRECOMPUTE:
                this.allocator = moduleManager.getModule(
                        "TacticsPoliceOffice.TargetAllocator",
                        "adf.sample.module.complex.SamplePoliceTargetAllocator");
                this.picker = moduleManager.getCommandPicker(
                        "TacticsPoliceOffice.CommandPicker",
                        "adf.sample.centralized.CommandPickerPolice");
        }
        registerModule(this.allocator);
        registerModule(this.picker);

        this.isVisualDebug = (scenarioInfo.isDebugMode()
                && moduleManager.getModuleConfig().getBooleanValue("VisualDebug", false));
    }

    @Override
    public void think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData)
    {
        modulesUpdateInfo(messageManager);

        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }
        Map<EntityID, EntityID> allocatorResult = this.allocator.calc().getResult();
        for (CommunicationMessage message : this.picker.setAllocatorResult(allocatorResult).calc().getResult())
        {
            messageManager.addMessage(message);
        }
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData debugData)
    {
        modulesResume(precomputeData);

        this.allocator.resume(precomputeData);
        this.picker.resume(precomputeData);
        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData debugData)
    {
        modulesPreparate();

        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }
    }
}
