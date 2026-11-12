// Made with Blockbench 5.2.2
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class BronzePuppetRebuildModel<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "bronzepuppetrebuildmodel"), "main");
	private final ModelPart head;
	private final ModelPart hat;
	private final ModelPart body;
	private final ModelPart right_arm;
	private final ModelPart left_arm;
	private final ModelPart right_leg;
	private final ModelPart left_leg;

	public BronzePuppetRebuildModel(ModelPart root) {
		this.head = root.getChild("head");
		this.hat = root.getChild("hat");
		this.body = root.getChild("body");
		this.right_arm = root.getChild("right_arm");
		this.left_arm = root.getChild("left_arm");
		this.right_leg = root.getChild("right_leg");
		this.left_leg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(97, 1).addBox(-2.0F, -1.3F, -1.9F, 4.0F, 2.4F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.6F, -8.2F, -2.95F, 7.2F, 7.0F, 6.1F, new CubeDeformation(0.0F))
		.texOffs(65, 1).addBox(-3.8F, -8.0F, 2.85F, 7.6F, 6.8F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(97, 33).addBox(-3.95F, -7.6F, -3.7F, 7.9F, 6.8F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(33, 1).addBox(-3.75F, -8.1F, -4.15F, 7.5F, 1.2F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(65, 1).addBox(-0.75F, -9.5F, -3.05F, 1.5F, 2.4F, 6.5F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-0.6F, -6.2F, -4.9F, 1.2F, 3.0F, 1.4F, new CubeDeformation(0.0F))
		.texOffs(33, 1).addBox(-1.0F, -3.8F, -4.8F, 2.0F, 1.0F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(97, 1).addBox(-1.85F, -3.05F, -4.25F, 3.7F, 1.3F, 0.7F, new CubeDeformation(0.0F))
		.texOffs(1, 97).addBox(-1.6F, -2.7F, -4.5F, 3.2F, 0.7F, 0.4F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-2.85F, -1.75F, -4.1F, 5.7F, 1.3F, 1.6F, new CubeDeformation(0.0F))
		.texOffs(97, 1).addBox(0.8F, -5.8F, -4.25F, 2.4F, 1.6F, 0.7F, new CubeDeformation(0.0F))
		.texOffs(35, 99).addBox(1.15F, -5.2F, -4.95F, 1.7F, 0.55F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(97, 1).addBox(-3.2F, -5.8F, -4.25F, 2.4F, 1.6F, 0.7F, new CubeDeformation(0.0F))
		.texOffs(35, 99).addBox(-2.85F, -5.2F, -4.95F, 1.7F, 0.55F, 0.25F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition 冠角1_r1 = head.addOrReplaceChild("冠角1_r1", CubeListBuilder.create().texOffs(1, 1).addBox(-0.6F, -1.35F, -2.2F, 1.2F, 2.7F, 4.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.7F, -8.1F, 0.2F, 0.0F, 0.0F, -0.2618F));

		PartDefinition 耳翼1_r1 = head.addOrReplaceChild("耳翼1_r1", CubeListBuilder.create().texOffs(65, 1).addBox(-0.6F, -2.4F, -1.75F, 1.2F, 4.8F, 3.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.25F, -4.4F, 0.0F, 0.0F, 0.0F, 0.1396F));

		PartDefinition 颧骨1_r1 = head.addOrReplaceChild("颧骨1_r1", CubeListBuilder.create().texOffs(97, 33).addBox(-0.95F, -1.05F, -0.5F, 1.9F, 2.1F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.7F, -3.5F, -4.0F, 0.0F, 0.0F, -0.2618F));

		PartDefinition 下压怒眉1_r1 = head.addOrReplaceChild("下压怒眉1_r1", CubeListBuilder.create().texOffs(33, 1).addBox(-1.5F, -0.4F, -0.4F, 3.0F, 0.8F, 0.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.0F, -5.65F, -4.35F, 0.0F, 0.0F, 0.3927F));

		PartDefinition 冠角_m1_r1 = head.addOrReplaceChild("冠角_m1_r1", CubeListBuilder.create().texOffs(1, 1).addBox(-0.6F, -1.35F, -2.2F, 1.2F, 2.7F, 4.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.7F, -8.1F, 0.2F, 0.0F, 0.0F, 0.2618F));

		PartDefinition 耳翼_m1_r1 = head.addOrReplaceChild("耳翼_m1_r1", CubeListBuilder.create().texOffs(65, 1).addBox(-0.6F, -2.4F, -1.75F, 1.2F, 4.8F, 3.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.25F, -4.4F, 0.0F, 0.0F, 0.0F, -0.1396F));

		PartDefinition 颧骨_m1_r1 = head.addOrReplaceChild("颧骨_m1_r1", CubeListBuilder.create().texOffs(97, 33).addBox(-0.95F, -1.05F, -0.5F, 1.9F, 2.1F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.7F, -3.5F, -4.0F, 0.0F, 0.0F, 0.2618F));

		PartDefinition 下压怒眉_m1_r1 = head.addOrReplaceChild("下压怒眉_m1_r1", CubeListBuilder.create().texOffs(33, 1).addBox(-1.5F, -0.4F, -0.4F, 3.0F, 0.8F, 0.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -5.65F, -4.35F, 0.0F, 0.0F, -0.3927F));

		PartDefinition hat = partdefinition.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(97, 1).addBox(-4.25F, 0.5F, -1.95F, 8.5F, 10.8F, 4.3F, new CubeDeformation(0.0F))
		.texOffs(1, 65).addBox(-4.7F, 1.25F, -3.1F, 9.4F, 5.5F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(33, 1).addBox(-5.0F, 0.975F, -3.3F, 10.0F, 0.65F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(97, 33).addBox(-2.15F, 2.0F, -3.75F, 4.3F, 4.0F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-0.4F, 3.0F, -4.25F, 0.8F, 2.0F, 0.6F, new CubeDeformation(0.0F))
		.texOffs(65, 1).addBox(3.85F, 2.5F, -2.35F, 1.2F, 6.0F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(65, 1).addBox(-5.05F, 2.5F, -2.35F, 1.2F, 6.0F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-4.25F, 8.0F, -3.25F, 8.5F, 2.0F, 1.5F, new CubeDeformation(0.0F))
		.texOffs(1, 33).addBox(-4.9F, 9.7F, -2.9F, 9.8F, 1.6F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(97, 33).addBox(-1.4F, 9.4F, -3.7F, 2.8F, 2.2F, 0.8F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition 后裙甲1_r1 = body.addOrReplaceChild("后裙甲1_r1", CubeListBuilder.create().texOffs(1, 1).addBox(-2.05F, -1.85F, -0.5F, 4.1F, 3.7F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 65).addBox(-2.05F, -1.85F, -6.0F, 4.1F, 3.7F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.35F, 12.3F, 2.7F, 0.0F, 0.0F, 0.0873F));

		PartDefinition 侧裙甲1_r1 = body.addOrReplaceChild("侧裙甲1_r1", CubeListBuilder.create().texOffs(65, 1).addBox(-0.55F, -1.85F, -2.25F, 1.1F, 3.7F, 4.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.55F, 12.2F, 0.0F, 0.0F, 0.0F, 0.1396F));

		PartDefinition 后裙甲_m1_r1 = body.addOrReplaceChild("后裙甲_m1_r1", CubeListBuilder.create().texOffs(1, 1).addBox(-2.05F, -1.85F, -0.5F, 4.1F, 3.7F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 65).addBox(-2.05F, -1.85F, -6.0F, 4.1F, 3.7F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.35F, 12.3F, 2.7F, 0.0F, 0.0F, -0.0873F));

		PartDefinition 侧裙甲_m1_r1 = body.addOrReplaceChild("侧裙甲_m1_r1", CubeListBuilder.create().texOffs(65, 1).addBox(-0.55F, -1.85F, -2.25F, 1.1F, 3.7F, 4.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.55F, 12.2F, 0.0F, 0.0F, 0.0F, -0.1396F));

		PartDefinition 背甲1_r1 = body.addOrReplaceChild("背甲1_r1", CubeListBuilder.create().texOffs(1, 1).addBox(-2.25F, -3.25F, -0.7F, 4.5F, 6.5F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.4F, 4.5F, 2.9F, 0.0F, 0.0F, 0.0873F));

		PartDefinition 胸镜卷角1_r1 = body.addOrReplaceChild("胸镜卷角1_r1", CubeListBuilder.create().texOffs(65, 1).addBox(-0.5F, -1.35F, -0.4F, 1.0F, 2.7F, 0.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.4F, 3.2F, -3.6F, 0.0F, 0.0F, 0.3927F));

		PartDefinition 背甲_m1_r1 = body.addOrReplaceChild("背甲_m1_r1", CubeListBuilder.create().texOffs(1, 1).addBox(-2.25F, -3.25F, -0.7F, 4.5F, 6.5F, 1.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.4F, 4.5F, 2.9F, 0.0F, 0.0F, -0.0873F));

		PartDefinition 胸镜卷角_m1_r1 = body.addOrReplaceChild("胸镜卷角_m1_r1", CubeListBuilder.create().texOffs(65, 1).addBox(-0.5F, -1.35F, -0.4F, 1.0F, 2.7F, 0.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.4F, 3.2F, -3.6F, 0.0F, 0.0F, -0.3927F));

		PartDefinition right_arm = partdefinition.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(97, 1).addBox(-1.75F, 0.0F, -1.8F, 3.5F, 10.0F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-2.6F, -1.3F, -2.5F, 5.2F, 3.2F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(97, 33).addBox(-2.3F, -1.1F, -3.05F, 4.6F, 2.2F, 0.7F, new CubeDeformation(0.0F))
		.texOffs(1, 65).addBox(-1.75F, 1.8F, -2.5F, 3.5F, 3.2F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(65, 33).addBox(-2.05F, 4.75F, -2.05F, 4.1F, 1.3F, 4.1F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-1.9F, 5.9F, -2.45F, 3.8F, 3.0F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(65, 1).addBox(-1.8F, 5.9F, 1.2F, 3.6F, 3.0F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(33, 1).addBox(-2.0F, 8.75F, -2.0F, 4.0F, 0.7F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(97, 33).addBox(-1.9F, 9.4F, -1.95F, 3.8F, 1.8F, 3.9F, new CubeDeformation(0.0F))
		.texOffs(1, 65).addBox(-1.65F, 9.7F, -2.65F, 3.3F, 1.2F, 0.7F, new CubeDeformation(0.0F)), PartPose.offset(6.0F, 2.0F, 0.0F));

		PartDefinition 肩翼_r1 = right_arm.addOrReplaceChild("肩翼_r1", CubeListBuilder.create().texOffs(65, 1).addBox(-0.65F, -1.7F, -2.55F, 1.3F, 3.4F, 5.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 0.1F, 0.0F, 0.0F, 0.0F, 0.2618F));

		PartDefinition left_arm = partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(97, 1).addBox(-1.75F, 0.0F, -1.8F, 3.5F, 10.0F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-2.6F, -1.3F, -2.5F, 5.2F, 3.2F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(97, 33).addBox(-2.3F, -1.1F, -3.05F, 4.6F, 2.2F, 0.7F, new CubeDeformation(0.0F))
		.texOffs(1, 65).addBox(-1.75F, 1.8F, -2.5F, 3.5F, 3.2F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(65, 33).addBox(-2.05F, 4.75F, -2.05F, 4.1F, 1.3F, 4.1F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-1.9F, 5.9F, -2.45F, 3.8F, 3.0F, 1.7F, new CubeDeformation(0.0F))
		.texOffs(65, 1).addBox(-1.8F, 5.9F, 1.2F, 3.6F, 3.0F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(33, 1).addBox(-2.0F, 8.75F, -2.0F, 4.0F, 0.7F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(97, 33).addBox(-1.9F, 9.4F, -1.95F, 3.8F, 1.8F, 3.9F, new CubeDeformation(0.0F))
		.texOffs(1, 65).addBox(-1.65F, 9.7F, -2.65F, 3.3F, 1.2F, 0.7F, new CubeDeformation(0.0F)), PartPose.offset(-6.0F, 2.0F, 0.0F));

		PartDefinition 肩翼_r2 = left_arm.addOrReplaceChild("肩翼_r2", CubeListBuilder.create().texOffs(65, 1).addBox(-0.65F, -1.7F, -2.55F, 1.3F, 3.4F, 5.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, 0.1F, 0.0F, 0.0F, 0.0F, -0.2618F));

		PartDefinition right_leg = partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(97, 1).addBox(-1.85F, 0.0F, -1.8F, 3.7F, 11.8F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(1, 65).addBox(-2.0F, 0.65F, -2.5F, 4.0F, 4.7F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(97, 33).addBox(-2.15F, 4.15F, -3.3F, 4.3F, 2.5F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-2.0F, 6.4F, -2.6F, 4.0F, 4.2F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(33, 1).addBox(-0.4F, 6.55F, -3.1F, 0.8F, 3.9F, 0.6F, new CubeDeformation(0.0F))
		.texOffs(65, 1).addBox(-2.0F, 6.2F, 1.35F, 4.0F, 4.4F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(97, 1).addBox(-2.2F, 10.6F, -3.3F, 4.4F, 1.4F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-2.25F, 9.75F, -4.0F, 4.5F, 1.3F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(33, 1).addBox(-2.25F, 10.0F, -4.35F, 4.5F, 1.2F, 0.5F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, 12.0F, 0.0F));

		PartDefinition left_leg = partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(97, 1).addBox(-1.85F, 0.0F, -1.8F, 3.7F, 11.8F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(1, 65).addBox(-2.0F, 0.65F, -2.5F, 4.0F, 4.7F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(97, 33).addBox(-2.15F, 4.15F, -3.3F, 4.3F, 2.5F, 1.8F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-2.0F, 6.4F, -2.6F, 4.0F, 4.2F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(33, 1).addBox(-0.4F, 6.55F, -3.1F, 0.8F, 3.9F, 0.6F, new CubeDeformation(0.0F))
		.texOffs(65, 1).addBox(-2.0F, 6.2F, 1.35F, 4.0F, 4.4F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(97, 1).addBox(-2.2F, 10.6F, -3.3F, 4.4F, 1.4F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-2.25F, 9.75F, -4.0F, 4.5F, 1.3F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(33, 1).addBox(-2.25F, 10.0F, -4.35F, 4.5F, 1.2F, 0.5F, new CubeDeformation(0.0F)), PartPose.offset(-2.5F, 12.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		hat.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		right_arm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		left_arm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		right_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		left_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}
