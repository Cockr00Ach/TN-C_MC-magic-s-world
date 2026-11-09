// Made with Blockbench 5.2.2
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class BronzePuppetFineModel<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "bronzepuppetfinemodel"), "main");
	private final ModelPart head;
	private final ModelPart hat;
	private final ModelPart body;
	private final ModelPart right_arm;
	private final ModelPart left_arm;
	private final ModelPart right_leg;
	private final ModelPart left_leg;

	public BronzePuppetFineModel(ModelPart root) {
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

		PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(171, 1).addBox(-4.176F, -10.0F, -3.65F, 8.352F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(205, 1).addBox(-4.286F, -9.5F, -3.65F, 8.572F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(1, 12).addBox(-4.396F, -9.0F, -3.65F, 8.792F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(36, 12).addBox(-4.506F, -8.5F, -3.65F, 9.012F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(71, 12).addBox(-4.55F, -8.0F, -3.65F, 9.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(106, 12).addBox(-4.55F, -7.5F, -3.65F, 9.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(141, 12).addBox(-4.55F, -7.0F, -3.65F, 9.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(176, 12).addBox(-4.55F, -6.5F, -3.65F, 9.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(211, 12).addBox(-4.55F, -6.0F, -3.65F, 9.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(1, 22).addBox(-4.55F, -5.5F, -3.65F, 9.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(36, 22).addBox(-4.55F, -5.0F, -3.65F, 9.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(71, 22).addBox(-4.55F, -4.5F, -3.65F, 9.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(106, 22).addBox(-4.55F, -4.0F, -3.65F, 9.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(141, 22).addBox(-4.55F, -3.5F, -3.65F, 9.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(176, 22).addBox(-4.55F, -3.0F, -3.65F, 9.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(211, 22).addBox(-4.425F, -2.5F, -3.65F, 8.85F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(1, 32).addBox(-4.3F, -2.0F, -3.65F, 8.6F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(35, 32).addBox(-4.175F, -1.5F, -3.65F, 8.35F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(69, 32).addBox(-4.05F, -1.0F, -3.65F, 8.1F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(102, 32).addBox(-3.925F, -0.5F, -3.65F, 7.85F, 0.5F, 7.3F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.9336F, -9.9F, -3.95F, 7.8672F, 0.32F, 7.8F, new CubeDeformation(0.0F))
		.texOffs(35, 1).addBox(-4.0168F, -9.58F, -3.95F, 8.0336F, 0.32F, 7.8F, new CubeDeformation(0.0F))
		.texOffs(69, 1).addBox(-4.1F, -9.26F, -3.95F, 8.2F, 0.32F, 7.8F, new CubeDeformation(0.0F))
		.texOffs(103, 1).addBox(-4.0168F, -8.94F, -3.95F, 8.0336F, 0.32F, 7.8F, new CubeDeformation(0.0F))
		.texOffs(137, 1).addBox(-3.9336F, -8.62F, -3.95F, 7.8672F, 0.32F, 7.8F, new CubeDeformation(0.0F))
		.texOffs(246, 12).addBox(4.08F, -9.6F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(246, 22).addBox(3.83F, -9.83F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(245, 32).addBox(3.58F, -10.06F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(245, 103).addBox(3.33F, -10.29F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(247, 111).addBox(3.08F, -10.52F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(248, 150).addBox(2.83F, -10.75F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(27, 162).addBox(0.72F, -6.15F, -3.89F, 2.64F, 1.35F, 0.32F, new CubeDeformation(0.0F))
		.texOffs(168, 177).addBox(1.04F, -5.77F, -4.04F, 2.0F, 0.42F, 0.19F, new CubeDeformation(0.0F))
		.texOffs(150, 177).addBox(1.85F, -5.82F, -4.13F, 0.38F, 0.54F, 0.11F, new CubeDeformation(0.0F))
		.texOffs(100, 166).addBox(2.9348F, -4.8F, -4.25F, 1.0304F, 0.55F, 0.68F, new CubeDeformation(0.0F))
		.texOffs(106, 166).addBox(2.8516F, -4.25F, -4.25F, 1.1968F, 0.55F, 0.68F, new CubeDeformation(0.0F))
		.texOffs(112, 166).addBox(2.8516F, -3.7F, -4.25F, 1.1968F, 0.55F, 0.68F, new CubeDeformation(0.0F))
		.texOffs(118, 166).addBox(2.9348F, -3.15F, -4.25F, 1.0304F, 0.55F, 0.68F, new CubeDeformation(0.0F))
		.texOffs(75, 189).addBox(2.5456F, -8.7372F, -4.06F, 1.0044F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(158, 166).addBox(2.5828F, -8.7F, -4.06F, 0.0744F, 1.1284F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(80, 189).addBox(2.5456F, -7.6832F, -4.06F, 0.93F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(57, 177).addBox(3.4384F, -8.297F, -4.06F, 0.0744F, 0.7254F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(85, 189).addBox(2.9114F, -8.3342F, -4.06F, 0.5642F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(169, 180).addBox(2.9486F, -8.297F, -4.06F, 0.0744F, 0.3658F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(131, 189).addBox(2.8928F, -3.3336F, -4.16F, 0.9072F, 0.0672F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(181, 166).addBox(2.9264F, -3.3F, -4.16F, 0.0672F, 1.0192F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(136, 189).addBox(2.8928F, -2.3816F, -4.16F, 0.84F, 0.0672F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(69, 177).addBox(3.6992F, -2.936F, -4.16F, 0.0672F, 0.6552F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(140, 189).addBox(3.2232F, -2.9696F, -4.16F, 0.5096F, 0.0672F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(193, 180).addBox(3.2568F, -2.936F, -4.16F, 0.0672F, 0.3304F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(149, 156).addBox(4.15F, -6.4F, -0.4F, 0.86F, 1.2F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(236, 170).addBox(4.77F, -6.05F, 0.85F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(241, 170).addBox(4.77F, -5.25F, 0.7026F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(246, 170).addBox(4.77F, -4.6644F, 0.3F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(251, 170).addBox(4.77F, -4.45F, -0.25F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(1, 174).addBox(4.77F, -4.6644F, -0.8F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(6, 174).addBox(4.77F, -5.25F, -1.2026F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(11, 174).addBox(4.77F, -6.05F, -1.35F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(16, 174).addBox(4.77F, -6.85F, -1.2026F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(21, 174).addBox(4.77F, -7.4356F, -0.8F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(26, 174).addBox(4.77F, -7.65F, -0.25F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(31, 174).addBox(4.77F, -7.4356F, 0.3F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(36, 174).addBox(4.77F, -6.85F, 0.7026F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(247, 180).addBox(3.56F, -8.64F, -4.09F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(186, 183).addBox(3.6188F, -8.5812F, -4.15F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(250, 180).addBox(3.56F, -1.44F, -4.09F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(189, 183).addBox(3.6188F, -1.3812F, -4.15F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(1, 156).addBox(-4.72F, -9.6F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(10, 156).addBox(-4.47F, -9.83F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(19, 156).addBox(-4.22F, -10.06F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(28, 156).addBox(-3.97F, -10.29F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(37, 156).addBox(-3.72F, -10.52F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(46, 156).addBox(-3.47F, -10.75F, -2.0F, 0.64F, 0.42F, 2.7F, new CubeDeformation(0.0F))
		.texOffs(35, 162).addBox(-3.36F, -6.15F, -3.89F, 2.64F, 1.35F, 0.32F, new CubeDeformation(0.0F))
		.texOffs(175, 177).addBox(-3.04F, -5.77F, -4.04F, 2.0F, 0.42F, 0.19F, new CubeDeformation(0.0F))
		.texOffs(153, 177).addBox(-2.23F, -5.82F, -4.13F, 0.38F, 0.54F, 0.11F, new CubeDeformation(0.0F))
		.texOffs(124, 166).addBox(-3.9652F, -4.8F, -4.25F, 1.0304F, 0.55F, 0.68F, new CubeDeformation(0.0F))
		.texOffs(130, 166).addBox(-4.0484F, -4.25F, -4.25F, 1.1968F, 0.55F, 0.68F, new CubeDeformation(0.0F))
		.texOffs(136, 166).addBox(-4.0484F, -3.7F, -4.25F, 1.1968F, 0.55F, 0.68F, new CubeDeformation(0.0F))
		.texOffs(142, 166).addBox(-3.9652F, -3.15F, -4.25F, 1.0304F, 0.55F, 0.68F, new CubeDeformation(0.0F))
		.texOffs(89, 189).addBox(-3.6244F, -8.7372F, -4.06F, 1.0044F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(161, 166).addBox(-2.6572F, -8.7F, -4.06F, 0.0744F, 1.1284F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(94, 189).addBox(-3.55F, -7.6832F, -4.06F, 0.93F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(60, 177).addBox(-3.5128F, -8.297F, -4.06F, 0.0744F, 0.7254F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(99, 189).addBox(-3.55F, -8.3342F, -4.06F, 0.5642F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(172, 180).addBox(-3.023F, -8.297F, -4.06F, 0.0744F, 0.3658F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(144, 189).addBox(-3.8672F, -3.3336F, -4.16F, 0.9072F, 0.0672F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(184, 166).addBox(-2.9936F, -3.3F, -4.16F, 0.0672F, 1.0192F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(149, 189).addBox(-3.8F, -2.3816F, -4.16F, 0.84F, 0.0672F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(72, 177).addBox(-3.7664F, -2.936F, -4.16F, 0.0672F, 0.6552F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(153, 189).addBox(-3.8F, -2.9696F, -4.16F, 0.5096F, 0.0672F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(196, 180).addBox(-3.324F, -2.936F, -4.16F, 0.0672F, 0.3304F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(155, 156).addBox(-5.01F, -6.4F, -0.4F, 0.86F, 1.2F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(41, 174).addBox(-5.33F, -6.05F, 0.85F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(46, 174).addBox(-5.33F, -5.25F, 0.7026F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(51, 174).addBox(-5.33F, -4.6644F, 0.3F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(56, 174).addBox(-5.33F, -4.45F, -0.25F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(61, 174).addBox(-5.33F, -4.6644F, -0.8F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(66, 174).addBox(-5.33F, -5.25F, -1.2026F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(71, 174).addBox(-5.33F, -6.05F, -1.35F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(76, 174).addBox(-5.33F, -6.85F, -1.2026F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(81, 174).addBox(-5.33F, -7.4356F, -0.8F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(86, 174).addBox(-5.33F, -7.65F, -0.25F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(91, 174).addBox(-5.33F, -7.4356F, 0.3F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(96, 174).addBox(-5.33F, -6.85F, 0.7026F, 0.56F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(253, 180).addBox(-3.84F, -8.64F, -4.09F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(192, 183).addBox(-3.7812F, -8.5812F, -4.15F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(1, 183).addBox(-3.84F, -1.44F, -4.09F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(195, 183).addBox(-3.7812F, -1.3812F, -4.15F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(207, 166).addBox(-0.3F, -7.1F, -4.25F, 0.6F, 0.4F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(193, 166).addBox(-0.319F, -6.7F, -4.315F, 0.638F, 0.4F, 0.715F, new CubeDeformation(0.0F))
		.texOffs(170, 166).addBox(-0.338F, -6.3F, -4.38F, 0.676F, 0.4F, 0.78F, new CubeDeformation(0.0F))
		.texOffs(94, 166).addBox(-0.357F, -5.9F, -4.445F, 0.714F, 0.4F, 0.845F, new CubeDeformation(0.0F))
		.texOffs(58, 166).addBox(-0.376F, -5.5F, -4.51F, 0.752F, 0.4F, 0.91F, new CubeDeformation(0.0F))
		.texOffs(49, 166).addBox(-0.395F, -5.1F, -4.575F, 0.79F, 0.4F, 0.975F, new CubeDeformation(0.0F))
		.texOffs(248, 162).addBox(-0.414F, -4.7F, -4.64F, 0.828F, 0.4F, 1.04F, new CubeDeformation(0.0F))
		.texOffs(104, 162).addBox(-0.433F, -4.3F, -4.705F, 0.866F, 0.4F, 1.105F, new CubeDeformation(0.0F))
		.texOffs(76, 162).addBox(-0.452F, -3.9F, -4.77F, 0.904F, 0.4F, 1.17F, new CubeDeformation(0.0F))
		.texOffs(83, 162).addBox(-0.471F, -3.5F, -4.77F, 0.942F, 0.4F, 1.17F, new CubeDeformation(0.0F))
		.texOffs(90, 162).addBox(-0.49F, -3.1F, -4.77F, 0.98F, 0.4F, 1.17F, new CubeDeformation(0.0F))
		.texOffs(97, 162).addBox(-0.509F, -2.7F, -4.77F, 1.018F, 0.4F, 1.17F, new CubeDeformation(0.0F))
		.texOffs(148, 166).addBox(0.32F, -3.2F, -4.72F, 0.64F, 0.58F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(160, 183).addBox(0.51F, -2.8F, -4.81F, 0.28F, 0.18F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(153, 166).addBox(-0.96F, -3.2F, -4.72F, 0.64F, 0.58F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(163, 183).addBox(-0.79F, -2.8F, -4.81F, 0.28F, 0.18F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(101, 174).addBox(-2.244F, -1.6F, -4.18F, 4.488F, 0.3F, 0.7F, new CubeDeformation(0.0F))
		.texOffs(114, 174).addBox(-2.322F, -1.3F, -4.18F, 4.644F, 0.3F, 0.7F, new CubeDeformation(0.0F))
		.texOffs(127, 174).addBox(-2.4F, -1.0F, -4.18F, 4.8F, 0.3F, 0.7F, new CubeDeformation(0.0F))
		.texOffs(140, 174).addBox(-2.322F, -0.7F, -4.18F, 4.644F, 0.3F, 0.7F, new CubeDeformation(0.0F))
		.texOffs(153, 174).addBox(-2.244F, -0.4F, -4.18F, 4.488F, 0.3F, 0.7F, new CubeDeformation(0.0F))
		.texOffs(81, 177).addBox(1.07F, -1.07F, -4.42F, 0.16F, 0.45F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(84, 177).addBox(0.71F, -1.07F, -4.42F, 0.16F, 0.45F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(87, 177).addBox(0.35F, -1.07F, -4.42F, 0.16F, 0.45F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(90, 177).addBox(-0.01F, -1.07F, -4.42F, 0.16F, 0.45F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(93, 177).addBox(-0.37F, -1.07F, -4.42F, 0.16F, 0.45F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(96, 177).addBox(-0.73F, -1.07F, -4.42F, 0.16F, 0.45F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(99, 177).addBox(-1.09F, -1.07F, -4.42F, 0.16F, 0.45F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(216, 186).addBox(1.847F, -8.539F, 3.68F, 1.053F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(70, 166).addBox(1.886F, -8.5F, 3.68F, 0.078F, 1.183F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(221, 186).addBox(1.847F, -7.434F, 3.68F, 0.975F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(33, 177).addBox(2.783F, -8.0775F, 3.68F, 0.078F, 0.7605F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(226, 186).addBox(2.2305F, -8.1165F, 3.68F, 0.5915F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(145, 180).addBox(2.2695F, -8.0775F, 3.68F, 0.078F, 0.3835F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(230, 186).addBox(-1.753F, -8.539F, 3.68F, 1.053F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(73, 166).addBox(-1.714F, -8.5F, 3.68F, 0.078F, 1.183F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(235, 186).addBox(-1.753F, -7.434F, 3.68F, 0.975F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(36, 177).addBox(-0.817F, -8.0775F, 3.68F, 0.078F, 0.7605F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(240, 186).addBox(-1.3695F, -8.1165F, 3.68F, 0.5915F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(148, 180).addBox(-1.3305F, -8.0775F, 3.68F, 0.078F, 0.3835F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(244, 186).addBox(1.847F, -6.839F, 3.68F, 1.053F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(76, 166).addBox(1.886F, -6.8F, 3.68F, 0.078F, 1.183F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(249, 186).addBox(1.847F, -5.734F, 3.68F, 0.975F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(39, 177).addBox(2.783F, -6.3775F, 3.68F, 0.078F, 0.7605F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(1, 189).addBox(2.2305F, -6.4165F, 3.68F, 0.5915F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(151, 180).addBox(2.2695F, -6.3775F, 3.68F, 0.078F, 0.3835F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(5, 189).addBox(-1.753F, -6.839F, 3.68F, 1.053F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(79, 166).addBox(-1.714F, -6.8F, 3.68F, 0.078F, 1.183F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(10, 189).addBox(-1.753F, -5.734F, 3.68F, 0.975F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(42, 177).addBox(-0.817F, -6.3775F, 3.68F, 0.078F, 0.7605F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(15, 189).addBox(-1.3695F, -6.4165F, 3.68F, 0.5915F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(154, 180).addBox(-1.3305F, -6.3775F, 3.68F, 0.078F, 0.3835F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(19, 189).addBox(1.847F, -5.139F, 3.68F, 1.053F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(82, 166).addBox(1.886F, -5.1F, 3.68F, 0.078F, 1.183F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(24, 189).addBox(1.847F, -4.034F, 3.68F, 0.975F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(45, 177).addBox(2.783F, -4.6775F, 3.68F, 0.078F, 0.7605F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(29, 189).addBox(2.2305F, -4.7165F, 3.68F, 0.5915F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(157, 180).addBox(2.2695F, -4.6775F, 3.68F, 0.078F, 0.3835F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(33, 189).addBox(-1.753F, -5.139F, 3.68F, 1.053F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(85, 166).addBox(-1.714F, -5.1F, 3.68F, 0.078F, 1.183F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(38, 189).addBox(-1.753F, -4.034F, 3.68F, 0.975F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(48, 177).addBox(-0.817F, -4.6775F, 3.68F, 0.078F, 0.7605F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(43, 189).addBox(-1.3695F, -4.7165F, 3.68F, 0.5915F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(160, 180).addBox(-1.3305F, -4.6775F, 3.68F, 0.078F, 0.3835F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(47, 189).addBox(1.847F, -3.439F, 3.68F, 1.053F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(88, 166).addBox(1.886F, -3.4F, 3.68F, 0.078F, 1.183F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(52, 189).addBox(1.847F, -2.334F, 3.68F, 0.975F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(51, 177).addBox(2.783F, -2.9775F, 3.68F, 0.078F, 0.7605F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(57, 189).addBox(2.2305F, -3.0165F, 3.68F, 0.5915F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(163, 180).addBox(2.2695F, -2.9775F, 3.68F, 0.078F, 0.3835F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(61, 189).addBox(-1.753F, -3.439F, 3.68F, 1.053F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(91, 166).addBox(-1.714F, -3.4F, 3.68F, 0.078F, 1.183F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(66, 189).addBox(-1.753F, -2.334F, 3.68F, 0.975F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(54, 177).addBox(-0.817F, -2.9775F, 3.68F, 0.078F, 0.7605F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(71, 189).addBox(-1.3695F, -3.0165F, 3.68F, 0.5915F, 0.078F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(166, 180).addBox(-1.3305F, -2.9775F, 3.68F, 0.078F, 0.3835F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(1, 201).addBox(4.5F, -7.75F, -0.65F, 0.8F, 0.4F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(8, 201).addBox(4.5F, -4.08F, -0.65F, 0.8F, 0.4F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(251, 18).addBox(3.25F, -2.2F, 3.7F, 0.8F, 1.4F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(15, 201).addBox(-5.3F, -7.75F, -0.65F, 0.8F, 0.4F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(22, 201).addBox(-5.3F, -4.08F, -0.65F, 0.8F, 0.4F, 1.3F, new CubeDeformation(0.0F))
		.texOffs(252, 38).addBox(-4.05F, -2.2F, 3.7F, 0.8F, 1.4F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(102, 202).addBox(-0.825F, -10.1F, -4.11F, 1.65F, 0.75F, 0.55F, new CubeDeformation(0.0F))
		.texOffs(246, 28).addBox(-0.525F, -10.62F, -3.75F, 1.05F, 0.75F, 1.2F, new CubeDeformation(0.0F))
		.texOffs(47, 195).addBox(-0.31F, -10.95F, -2.8F, 0.62F, 0.65F, 2.3F, new CubeDeformation(0.0F))
		.texOffs(233, 203).addBox(-0.8F, -0.45F, -4.26F, 1.6F, 0.45F, 0.65F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition refined_crown_end_R_226_r1 = head.addOrReplaceChild("refined_crown_end_R_226_r1", CubeListBuilder.create().texOffs(214, 192).addBox(-0.35F, -0.34F, -2.4F, 0.7F, 0.68F, 4.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.45F, -9.81F, -0.5F, 0.0F, 0.0F, 0.1396F));

		PartDefinition refined_inner_brow_facet_R_225_r1 = head.addOrReplaceChild("refined_inner_brow_facet_R_225_r1", CubeListBuilder.create().texOffs(233, 199).addBox(-0.24F, -0.4F, -0.2F, 0.48F, 0.8F, 0.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.61F, -6.25F, -4.4F, 0.0F, 0.0F, 0.1745F));

		PartDefinition refined_nasal_wing_facet_R_224_r1 = head.addOrReplaceChild("refined_nasal_wing_facet_R_224_r1", CubeListBuilder.create().texOffs(186, 204).addBox(-0.22F, -0.21F, -0.18F, 0.44F, 0.42F, 0.36F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.72F, -2.94F, -4.68F, 0.0F, 0.0F, -0.3142F));

		PartDefinition refined_rear_neck_lame_R_223_r1 = head.addOrReplaceChild("refined_rear_neck_lame_R_223_r1", CubeListBuilder.create().texOffs(75, 200).addBox(-0.7F, -0.45F, -0.5F, 1.4F, 0.9F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.6F, -0.45F, 3.25F, 0.0F, 0.0F, 0.1396F));

		PartDefinition refined_ear_hinge_R_221_r1 = head.addOrReplaceChild("refined_ear_hinge_R_221_r1", CubeListBuilder.create().texOffs(114, 202).addBox(-0.25F, -0.3F, -0.35F, 0.5F, 0.6F, 0.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.1F, -5.7F, 0.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition refined_cheek_return_R_218_r1 = head.addOrReplaceChild("refined_cheek_return_R_218_r1", CubeListBuilder.create().texOffs(246, 18).addBox(-0.22F, -0.65F, -0.34F, 0.44F, 1.3F, 0.68F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.8F, -3.55F, -3.81F, 0.0F, -0.3142F, 0.2618F));

		PartDefinition refined_jaw_corner_R_217_r1 = head.addOrReplaceChild("refined_jaw_corner_R_217_r1", CubeListBuilder.create().texOffs(249, 8).addBox(-0.3F, -0.6F, -0.39F, 0.6F, 1.2F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.35F, -1.4F, -3.61F, 0.0F, 0.0F, -0.3142F));

		PartDefinition refined_temple_lower_R_216_r1 = head.addOrReplaceChild("refined_temple_lower_R_216_r1", CubeListBuilder.create().texOffs(41, 195).addBox(-0.3F, -0.9F, -0.575F, 0.6F, 1.8F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5F, -2.85F, -2.525F, 0.0F, -0.2094F, -0.1222F));

		PartDefinition refined_temple_upper_R_215_r1 = head.addOrReplaceChild("refined_temple_upper_R_215_r1", CubeListBuilder.create().texOffs(61, 195).addBox(-0.28F, -0.625F, -0.625F, 0.56F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.35F, -7.325F, -2.725F, 0.0F, -0.2094F, 0.0F));

		PartDefinition refined_brow_end_R_214_r1 = head.addOrReplaceChild("refined_brow_end_R_214_r1", CubeListBuilder.create().texOffs(181, 201).addBox(-0.29F, -0.425F, -0.25F, 0.58F, 0.85F, 0.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.27F, -6.425F, -4.12F, 0.0F, -0.1745F, 0.3142F));

		PartDefinition refined_crown_end_L_213_r1 = head.addOrReplaceChild("refined_crown_end_L_213_r1", CubeListBuilder.create().texOffs(201, 192).addBox(-0.35F, -0.34F, -2.4F, 0.7F, 0.68F, 4.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.45F, -9.81F, -0.5F, 0.0F, 0.0F, -0.1396F));

		PartDefinition refined_inner_brow_facet_L_212_r1 = head.addOrReplaceChild("refined_inner_brow_facet_L_212_r1", CubeListBuilder.create().texOffs(253, 28).addBox(-0.24F, -0.4F, -0.2F, 0.48F, 0.8F, 0.4F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.61F, -6.25F, -4.4F, 0.0F, 0.0F, -0.1745F));

		PartDefinition refined_nasal_wing_facet_L_211_r1 = head.addOrReplaceChild("refined_nasal_wing_facet_L_211_r1", CubeListBuilder.create().texOffs(86, 204).addBox(-0.22F, -0.21F, -0.18F, 0.44F, 0.42F, 0.36F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.72F, -2.94F, -4.68F, 0.0F, 0.0F, 0.3142F));

		PartDefinition refined_rear_neck_lame_L_210_r1 = head.addOrReplaceChild("refined_rear_neck_lame_L_210_r1", CubeListBuilder.create().texOffs(245, 38).addBox(-0.7F, -0.45F, -0.5F, 1.4F, 0.9F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.6F, -0.45F, 3.25F, 0.0F, 0.0F, -0.1396F));

		PartDefinition refined_ear_hinge_L_208_r1 = head.addOrReplaceChild("refined_ear_hinge_L_208_r1", CubeListBuilder.create().texOffs(109, 202).addBox(-0.25F, -0.3F, -0.35F, 0.5F, 0.6F, 0.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.1F, -5.7F, 0.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition refined_cheek_return_L_205_r1 = head.addOrReplaceChild("refined_cheek_return_L_205_r1", CubeListBuilder.create().texOffs(244, 8).addBox(-0.22F, -0.65F, -0.34F, 0.44F, 1.3F, 0.68F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.8F, -3.55F, -3.81F, 0.0F, 0.3142F, -0.2618F));

		PartDefinition refined_jaw_corner_L_204_r1 = head.addOrReplaceChild("refined_jaw_corner_L_204_r1", CubeListBuilder.create().texOffs(239, 8).addBox(-0.3F, -0.6F, -0.39F, 0.6F, 1.2F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.35F, -1.4F, -3.61F, 0.0F, 0.0F, 0.3142F));

		PartDefinition refined_temple_lower_L_203_r1 = head.addOrReplaceChild("refined_temple_lower_L_203_r1", CubeListBuilder.create().texOffs(35, 195).addBox(-0.3F, -0.9F, -0.575F, 0.6F, 1.8F, 1.15F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, -2.85F, -2.525F, 0.0F, 0.2094F, 0.1222F));

		PartDefinition refined_temple_upper_L_202_r1 = head.addOrReplaceChild("refined_temple_upper_L_202_r1", CubeListBuilder.create().texOffs(55, 195).addBox(-0.28F, -0.625F, -0.625F, 0.56F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.35F, -7.325F, -2.725F, 0.0F, 0.2094F, 0.0F));

		PartDefinition refined_brow_end_L_201_r1 = head.addOrReplaceChild("refined_brow_end_L_201_r1", CubeListBuilder.create().texOffs(176, 201).addBox(-0.29F, -0.425F, -0.25F, 0.58F, 0.85F, 0.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.27F, -6.425F, -4.12F, 0.0F, 0.1745F, -0.3142F));

		PartDefinition downturned_lip_140_r1 = head.addOrReplaceChild("downturned_lip_140_r1", CubeListBuilder.create().texOffs(140, 180).addBox(-0.4162F, -0.11F, -0.135F, 0.8324F, 0.22F, 0.27F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.4F, -1.855F, -4.135F, 0.0F, 0.0F, -0.2799F));

		PartDefinition downturned_lip_139_r1 = head.addOrReplaceChild("downturned_lip_139_r1", CubeListBuilder.create().texOffs(135, 180).addBox(-0.4069F, -0.11F, -0.135F, 0.8139F, 0.22F, 0.27F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5999F, -2.045F, -4.135F, 0.0F, 0.0F, -0.1853F));

		PartDefinition stern_mouth_seam_138_r1 = head.addOrReplaceChild("stern_mouth_seam_138_r1", CubeListBuilder.create().texOffs(53, 183).addBox(-0.5142F, -0.09F, -0.09F, 1.0284F, 0.18F, 0.18F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.4F, -2.06F, -4.01F, 0.0F, 0.0F, -0.2355F));

		PartDefinition stern_mouth_seam_137_r1 = head.addOrReplaceChild("stern_mouth_seam_137_r1", CubeListBuilder.create().texOffs(49, 183).addBox(-0.3431F, -0.09F, -0.09F, 0.6862F, 0.18F, 0.18F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.575F, -2.29F, -4.01F, 0.0F, 0.0F, -0.3264F));

		PartDefinition downturned_lip_134_r1 = head.addOrReplaceChild("downturned_lip_134_r1", CubeListBuilder.create().texOffs(130, 180).addBox(-0.4162F, -0.11F, -0.135F, 0.8324F, 0.22F, 0.27F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.4F, -1.855F, -4.135F, 0.0F, 0.0F, -2.8616F));

		PartDefinition downturned_lip_133_r1 = head.addOrReplaceChild("downturned_lip_133_r1", CubeListBuilder.create().texOffs(125, 180).addBox(-0.407F, -0.11F, -0.135F, 0.8139F, 0.22F, 0.27F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.6F, -2.045F, -4.135F, 0.0F, 0.0F, -2.9562F));

		PartDefinition stern_mouth_seam_132_r1 = head.addOrReplaceChild("stern_mouth_seam_132_r1", CubeListBuilder.create().texOffs(44, 183).addBox(-0.5142F, -0.09F, -0.09F, 1.0284F, 0.18F, 0.18F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.4F, -2.06F, -4.01F, 0.0F, 0.0F, -2.906F));

		PartDefinition stern_mouth_seam_131_r1 = head.addOrReplaceChild("stern_mouth_seam_131_r1", CubeListBuilder.create().texOffs(40, 183).addBox(-0.3431F, -0.09F, -0.09F, 0.6862F, 0.18F, 0.18F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.575F, -2.29F, -4.01F, 0.0F, 0.0F, -2.8152F));

		PartDefinition cheek_fold_87_r1 = head.addOrReplaceChild("cheek_fold_87_r1", CubeListBuilder.create().texOffs(155, 183).addBox(-0.559F, -0.08F, -0.06F, 1.118F, 0.16F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.95F, -3.65F, -4.29F, 0.0F, 0.0F, -1.7506F));

		PartDefinition cheek_fold_86_r1 = head.addOrReplaceChild("cheek_fold_86_r1", CubeListBuilder.create().texOffs(151, 183).addBox(-0.3605F, -0.08F, -0.06F, 0.7211F, 0.16F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.3499F, -4.4F, -4.29F, 0.0F, 0.0F, -2.5536F));

		PartDefinition lower_eyelid_81_r1 = head.addOrReplaceChild("lower_eyelid_81_r1", CubeListBuilder.create().texOffs(118, 180).addBox(-1.1F, -0.11F, -0.145F, 2.2F, 0.22F, 0.29F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.04F, -5.13F, -4.045F, 0.0F, 0.0F, -0.0873F));

		PartDefinition angry_brow_80_r1 = head.addOrReplaceChild("angry_brow_80_r1", CubeListBuilder.create().texOffs(120, 162).addBox(-1.43F, -0.33F, -0.415F, 2.86F, 0.66F, 0.83F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.04F, -6.41F, -4.075F, 0.0F, 0.0F, 0.2269F));

		PartDefinition cheek_fold_41_r1 = head.addOrReplaceChild("cheek_fold_41_r1", CubeListBuilder.create().texOffs(146, 183).addBox(-0.559F, -0.08F, -0.06F, 1.118F, 0.16F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.95F, -3.65F, -4.29F, 0.0F, 0.0F, -1.3909F));

		PartDefinition cheek_fold_40_r1 = head.addOrReplaceChild("cheek_fold_40_r1", CubeListBuilder.create().texOffs(142, 183).addBox(-0.3605F, -0.08F, -0.06F, 0.7211F, 0.16F, 0.12F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.35F, -4.4F, -4.29F, 0.0F, 0.0F, -0.588F));

		PartDefinition lower_eyelid_35_r1 = head.addOrReplaceChild("lower_eyelid_35_r1", CubeListBuilder.create().texOffs(111, 180).addBox(-1.1F, -0.11F, -0.145F, 2.2F, 0.22F, 0.29F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.04F, -5.13F, -4.045F, 0.0F, 0.0F, 0.0873F));

		PartDefinition angry_brow_34_r1 = head.addOrReplaceChild("angry_brow_34_r1", CubeListBuilder.create().texOffs(110, 162).addBox(-1.43F, -0.33F, -0.415F, 2.86F, 0.66F, 0.83F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.04F, -6.41F, -4.075F, 0.0F, 0.0F, -0.2269F));

		PartDefinition hat = partdefinition.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(101, 143).addBox(-1.792F, -0.8F, -2.0F, 3.584F, 0.4F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(119, 143).addBox(-1.896F, -0.4F, -2.0F, 3.792F, 0.4F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(137, 143).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 0.4F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(155, 143).addBox(-1.896F, 0.4F, -2.0F, 3.792F, 0.4F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(173, 143).addBox(-1.792F, 0.8F, -2.0F, 3.584F, 0.4F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(78, 135).addBox(-2.25F, -0.55F, -2.25F, 4.5F, 0.17F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(98, 135).addBox(-2.25F, -0.21F, -2.25F, 4.5F, 0.17F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(118, 135).addBox(-2.25F, 0.13F, -2.25F, 4.5F, 0.17F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(138, 135).addBox(-2.25F, 0.47F, -2.25F, 4.5F, 0.17F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(1, 79).addBox(-5.25F, 0.0F, -2.75F, 10.5F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(35, 79).addBox(-5.195F, 0.5F, -2.75F, 10.39F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(69, 79).addBox(-5.14F, 1.0F, -2.75F, 10.28F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(103, 79).addBox(-5.085F, 1.5F, -2.75F, 10.17F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(137, 79).addBox(-5.03F, 2.0F, -2.75F, 10.06F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(171, 79).addBox(-4.975F, 2.5F, -2.75F, 9.95F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(204, 79).addBox(-4.92F, 3.0F, -2.75F, 9.84F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(1, 87).addBox(-4.865F, 3.5F, -2.75F, 9.73F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(34, 87).addBox(-4.81F, 4.0F, -2.75F, 9.62F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(67, 87).addBox(-4.755F, 4.5F, -2.75F, 9.51F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(100, 87).addBox(-4.7F, 5.0F, -2.75F, 9.4F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(132, 87).addBox(-4.645F, 5.5F, -2.75F, 9.29F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(164, 87).addBox(-4.59F, 6.0F, -2.75F, 9.18F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(196, 87).addBox(-4.535F, 6.5F, -2.75F, 9.07F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(1, 95).addBox(-4.48F, 7.0F, -2.75F, 8.96F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(32, 95).addBox(-4.425F, 7.5F, -2.75F, 8.85F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(63, 95).addBox(-4.37F, 8.0F, -2.75F, 8.74F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(94, 95).addBox(-4.315F, 8.5F, -2.75F, 8.63F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(125, 95).addBox(-4.26F, 9.0F, -2.75F, 8.52F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(156, 95).addBox(-4.205F, 9.5F, -2.75F, 8.41F, 0.5F, 5.5F, new CubeDeformation(0.0F))
		.texOffs(130, 162).addBox(0.6257F, 0.65F, -3.1F, 3.8486F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(141, 162).addBox(0.5172F, 1.45F, -3.1F, 4.0657F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(153, 162).addBox(0.4085F, 2.25F, -3.1F, 4.2829F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(165, 162).addBox(0.3F, 3.05F, -3.1F, 4.5F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(178, 162).addBox(0.4085F, 3.85F, -3.1F, 4.2829F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(190, 162).addBox(0.5172F, 4.65F, -3.1F, 4.0657F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(202, 162).addBox(0.6257F, 5.45F, -3.1F, 3.8486F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(28, 186).addBox(2.6976F, 1.1188F, -3.28F, 1.6524F, 0.1224F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(161, 156).addBox(2.7588F, 1.18F, -3.28F, 0.1224F, 1.8564F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(34, 186).addBox(2.6976F, 2.8528F, -3.28F, 1.53F, 0.1224F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(64, 166).addBox(4.1664F, 1.843F, -3.28F, 0.1224F, 1.1934F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(40, 186).addBox(3.2994F, 1.7818F, -3.28F, 0.9282F, 0.1224F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(75, 177).addBox(3.3606F, 1.843F, -3.28F, 0.1224F, 0.6018F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(103, 189).addBox(2.4256F, 3.5628F, -3.28F, 1.0044F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(164, 166).addBox(3.3928F, 3.6F, -3.28F, 0.0744F, 1.1284F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(108, 189).addBox(2.5F, 4.6168F, -3.28F, 0.93F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(63, 177).addBox(2.5372F, 4.003F, -3.28F, 0.0744F, 0.7254F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(113, 189).addBox(2.5F, 3.9658F, -3.28F, 0.5642F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(175, 180).addBox(3.027F, 4.003F, -3.28F, 0.0744F, 0.3658F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(4, 183).addBox(4.36F, 0.96F, -3.32F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(198, 183).addBox(4.4188F, 1.0188F, -3.38F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(7, 183).addBox(0.51F, 0.96F, -3.32F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(201, 183).addBox(0.5688F, 1.0188F, -3.38F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(10, 183).addBox(4.36F, 5.41F, -3.32F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(204, 183).addBox(4.4188F, 5.4688F, -3.38F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(13, 183).addBox(0.51F, 5.41F, -3.32F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(207, 183).addBox(0.5688F, 5.4688F, -3.38F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(166, 183).addBox(0.75F, 6.625F, -2.98F, 3.75F, 0.15F, 0.13F, new CubeDeformation(0.0F))
		.texOffs(167, 156).addBox(0.825F, 6.7F, -2.98F, 0.15F, 1.65F, 0.13F, new CubeDeformation(0.0F))
		.texOffs(185, 177).addBox(1.478F, 9.2F, -3.3F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(192, 177).addBox(1.4F, 9.5F, -3.3F, 2.2F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(199, 177).addBox(1.478F, 9.8F, -3.3F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(206, 177).addBox(1.478F, 9.85F, -3.325F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(213, 177).addBox(1.4F, 10.15F, -3.325F, 2.2F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(220, 177).addBox(1.478F, 10.45F, -3.325F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(227, 177).addBox(1.478F, 10.5F, -3.35F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(234, 177).addBox(1.4F, 10.8F, -3.35F, 2.2F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(241, 177).addBox(1.478F, 11.1F, -3.35F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(248, 177).addBox(1.478F, 11.15F, -3.375F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(1, 180).addBox(1.4F, 11.45F, -3.375F, 2.2F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(8, 180).addBox(1.478F, 11.75F, -3.375F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(213, 162).addBox(-4.4743F, 0.65F, -3.1F, 3.8486F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(224, 162).addBox(-4.5828F, 1.45F, -3.1F, 4.0657F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(236, 162).addBox(-4.6915F, 2.25F, -3.1F, 4.2829F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(1, 166).addBox(-4.8F, 3.05F, -3.1F, 4.5F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(14, 166).addBox(-4.6915F, 3.85F, -3.1F, 4.2829F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(26, 166).addBox(-4.5828F, 4.65F, -3.1F, 4.0657F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(38, 166).addBox(-4.4743F, 5.45F, -3.1F, 3.8486F, 0.8F, 0.65F, new CubeDeformation(0.0F))
		.texOffs(45, 186).addBox(-2.4024F, 1.1188F, -3.28F, 1.6524F, 0.1224F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(164, 156).addBox(-2.3412F, 1.18F, -3.28F, 0.1224F, 1.8564F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(51, 186).addBox(-2.4024F, 2.8528F, -3.28F, 1.53F, 0.1224F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(67, 166).addBox(-0.9336F, 1.843F, -3.28F, 0.1224F, 1.1934F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(57, 186).addBox(-1.8006F, 1.7818F, -3.28F, 0.9282F, 0.1224F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(78, 177).addBox(-1.7394F, 1.843F, -3.28F, 0.1224F, 0.6018F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(117, 189).addBox(-2.6744F, 3.5628F, -3.28F, 1.0044F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(167, 166).addBox(-1.7072F, 3.6F, -3.28F, 0.0744F, 1.1284F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(122, 189).addBox(-2.6F, 4.6168F, -3.28F, 0.93F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(66, 177).addBox(-2.5628F, 4.003F, -3.28F, 0.0744F, 0.7254F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(127, 189).addBox(-2.6F, 3.9658F, -3.28F, 0.5642F, 0.0744F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(178, 180).addBox(-2.073F, 4.003F, -3.28F, 0.0744F, 0.3658F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(16, 183).addBox(-0.74F, 0.96F, -3.32F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(210, 183).addBox(-0.6812F, 1.0188F, -3.38F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(19, 183).addBox(-4.59F, 0.96F, -3.32F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(213, 183).addBox(-4.5312F, 1.0188F, -3.38F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(22, 183).addBox(-0.74F, 5.41F, -3.32F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(216, 183).addBox(-0.6812F, 5.4688F, -3.38F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(25, 183).addBox(-4.59F, 5.41F, -3.32F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(219, 183).addBox(-4.5312F, 5.4688F, -3.38F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(176, 183).addBox(-4.35F, 6.625F, -2.98F, 3.75F, 0.15F, 0.13F, new CubeDeformation(0.0F))
		.texOffs(170, 156).addBox(-4.275F, 6.7F, -2.98F, 0.15F, 1.65F, 0.13F, new CubeDeformation(0.0F))
		.texOffs(15, 180).addBox(-3.522F, 9.2F, -3.3F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(22, 180).addBox(-3.6F, 9.5F, -3.3F, 2.2F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(29, 180).addBox(-3.522F, 9.8F, -3.3F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(36, 180).addBox(-3.522F, 9.85F, -3.325F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(43, 180).addBox(-3.6F, 10.15F, -3.325F, 2.2F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(50, 180).addBox(-3.522F, 10.45F, -3.325F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(57, 180).addBox(-3.522F, 10.5F, -3.35F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(64, 180).addBox(-3.6F, 10.8F, -3.35F, 2.2F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(71, 180).addBox(-3.522F, 11.1F, -3.35F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(78, 180).addBox(-3.522F, 11.15F, -3.375F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(85, 180).addBox(-3.6F, 11.45F, -3.375F, 2.2F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(92, 180).addBox(-3.522F, 11.75F, -3.375F, 2.044F, 0.3F, 0.3F, new CubeDeformation(0.0F))
		.texOffs(219, 42).addBox(-4.1733F, 8.5F, -3.15F, 8.3465F, 0.325F, 6.3F, new CubeDeformation(0.0F))
		.texOffs(1, 52).addBox(-4.2578F, 8.825F, -3.15F, 8.5155F, 0.325F, 6.3F, new CubeDeformation(0.0F))
		.texOffs(33, 52).addBox(-4.2578F, 9.15F, -3.15F, 8.5155F, 0.325F, 6.3F, new CubeDeformation(0.0F))
		.texOffs(65, 52).addBox(-4.1733F, 9.475F, -3.15F, 8.3465F, 0.325F, 6.3F, new CubeDeformation(0.0F))
		.texOffs(214, 174).addBox(-1.244F, 8.7F, -3.6F, 2.488F, 0.4F, 0.55F, new CubeDeformation(0.0F))
		.texOffs(223, 174).addBox(-1.348F, 9.1F, -3.6F, 2.696F, 0.4F, 0.55F, new CubeDeformation(0.0F))
		.texOffs(232, 174).addBox(-1.348F, 9.5F, -3.6F, 2.696F, 0.4F, 0.55F, new CubeDeformation(0.0F))
		.texOffs(241, 174).addBox(-1.244F, 9.9F, -3.6F, 2.488F, 0.4F, 0.55F, new CubeDeformation(0.0F))
		.texOffs(146, 186).addBox(-0.477F, 8.849F, -3.79F, 1.377F, 0.102F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(43, 162).addBox(-0.426F, 8.9F, -3.79F, 0.102F, 1.547F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(151, 186).addBox(-0.477F, 10.294F, -3.79F, 1.275F, 0.102F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(198, 166).addBox(0.747F, 9.4525F, -3.79F, 0.102F, 0.9945F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(156, 186).addBox(0.0245F, 9.4015F, -3.79F, 0.7735F, 0.102F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(182, 177).addBox(0.0755F, 9.4525F, -3.79F, 0.102F, 0.5015F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(58, 183).addBox(3.78F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(234, 183).addBox(3.8304F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(61, 183).addBox(3.08F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(237, 183).addBox(3.1304F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(64, 183).addBox(2.38F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(240, 183).addBox(2.4304F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(67, 183).addBox(1.68F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(243, 183).addBox(1.7304F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(70, 183).addBox(0.98F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(246, 183).addBox(1.0304F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(73, 183).addBox(0.28F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(249, 183).addBox(0.3304F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(76, 183).addBox(-0.42F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(252, 183).addBox(-0.3696F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(79, 183).addBox(-1.12F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(1, 186).addBox(-1.0696F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(82, 183).addBox(-1.82F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(4, 186).addBox(-1.7696F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(85, 183).addBox(-2.52F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(7, 186).addBox(-2.4696F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(88, 183).addBox(-3.22F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(10, 186).addBox(-3.1696F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(91, 183).addBox(-3.92F, 8.92F, -3.28F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(13, 186).addBox(-3.8696F, 8.9704F, -3.34F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(1, 177).addBox(-0.23F, 0.0F, 2.78F, 0.46F, 0.63F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(157, 189).addBox(2.8724F, 0.8712F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(166, 174).addBox(2.9012F, 0.9F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(161, 189).addBox(2.8724F, 1.6872F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(102, 177).addBox(3.5636F, 1.212F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(165, 189).addBox(3.1556F, 1.1832F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(199, 180).addBox(3.1844F, 1.212F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(169, 189).addBox(-2.8576F, 0.8712F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(169, 174).addBox(-2.1088F, 0.9F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(173, 189).addBox(-2.8F, 1.6872F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(105, 177).addBox(-2.7712F, 1.212F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(177, 189).addBox(-2.8F, 1.1832F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(202, 180).addBox(-2.392F, 1.212F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(5, 177).addBox(-0.23F, 0.95F, 2.78F, 0.46F, 0.63F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(181, 189).addBox(2.8724F, 1.7912F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(172, 174).addBox(2.9012F, 1.82F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(185, 189).addBox(2.8724F, 2.6072F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(108, 177).addBox(3.5636F, 2.132F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(189, 189).addBox(3.1556F, 2.1032F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(205, 180).addBox(3.1844F, 2.132F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(193, 189).addBox(-2.8576F, 1.7912F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(175, 174).addBox(-2.1088F, 1.82F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(197, 189).addBox(-2.8F, 2.6072F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(111, 177).addBox(-2.7712F, 2.132F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(201, 189).addBox(-2.8F, 2.1032F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(208, 180).addBox(-2.392F, 2.132F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(9, 177).addBox(-0.23F, 1.9F, 2.78F, 0.46F, 0.63F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(205, 189).addBox(2.8724F, 2.7112F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(178, 174).addBox(2.9012F, 2.74F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(209, 189).addBox(2.8724F, 3.5272F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(114, 177).addBox(3.5636F, 3.052F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(213, 189).addBox(3.1556F, 3.0232F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(211, 180).addBox(3.1844F, 3.052F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(217, 189).addBox(-2.8576F, 2.7112F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(181, 174).addBox(-2.1088F, 2.74F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(221, 189).addBox(-2.8F, 3.5272F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(117, 177).addBox(-2.7712F, 3.052F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(225, 189).addBox(-2.8F, 3.0232F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(214, 180).addBox(-2.392F, 3.052F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(13, 177).addBox(-0.23F, 2.85F, 2.78F, 0.46F, 0.63F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(229, 189).addBox(2.8724F, 3.6312F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(184, 174).addBox(2.9012F, 3.66F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(233, 189).addBox(2.8724F, 4.4472F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(120, 177).addBox(3.5636F, 3.972F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(237, 189).addBox(3.1556F, 3.9432F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(217, 180).addBox(3.1844F, 3.972F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(241, 189).addBox(-2.8576F, 3.6312F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(187, 174).addBox(-2.1088F, 3.66F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(245, 189).addBox(-2.8F, 4.4472F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(123, 177).addBox(-2.7712F, 3.972F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(249, 189).addBox(-2.8F, 3.9432F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(220, 180).addBox(-2.392F, 3.972F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(17, 177).addBox(-0.23F, 3.8F, 2.78F, 0.46F, 0.63F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(253, 189).addBox(2.8724F, 4.5512F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(190, 174).addBox(2.9012F, 4.58F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(1, 192).addBox(2.8724F, 5.3672F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(126, 177).addBox(3.5636F, 4.892F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(5, 192).addBox(3.1556F, 4.8632F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(223, 180).addBox(3.1844F, 4.892F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(9, 192).addBox(-2.8576F, 4.5512F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(193, 174).addBox(-2.1088F, 4.58F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(13, 192).addBox(-2.8F, 5.3672F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(129, 177).addBox(-2.7712F, 4.892F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(17, 192).addBox(-2.8F, 4.8632F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(226, 180).addBox(-2.392F, 4.892F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(21, 177).addBox(-0.23F, 4.75F, 2.78F, 0.46F, 0.63F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(21, 192).addBox(2.8724F, 5.4712F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(196, 174).addBox(2.9012F, 5.5F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(25, 192).addBox(2.8724F, 6.2872F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(132, 177).addBox(3.5636F, 5.812F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(29, 192).addBox(3.1556F, 5.7832F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(229, 180).addBox(3.1844F, 5.812F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(33, 192).addBox(-2.8576F, 5.4712F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(199, 174).addBox(-2.1088F, 5.5F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(37, 192).addBox(-2.8F, 6.2872F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(135, 177).addBox(-2.7712F, 5.812F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(41, 192).addBox(-2.8F, 5.7832F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(232, 180).addBox(-2.392F, 5.812F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(25, 177).addBox(-0.23F, 5.7F, 2.78F, 0.46F, 0.63F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(45, 192).addBox(2.8724F, 6.3912F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(202, 174).addBox(2.9012F, 6.42F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(49, 192).addBox(2.8724F, 7.2072F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(138, 177).addBox(3.5636F, 6.732F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(53, 192).addBox(3.1556F, 6.7032F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(235, 180).addBox(3.1844F, 6.732F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(57, 192).addBox(-2.8576F, 6.3912F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(205, 174).addBox(-2.1088F, 6.42F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(61, 192).addBox(-2.8F, 7.2072F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(141, 177).addBox(-2.7712F, 6.732F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(65, 192).addBox(-2.8F, 6.7032F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(238, 180).addBox(-2.392F, 6.732F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(29, 177).addBox(-0.23F, 6.65F, 2.78F, 0.46F, 0.63F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(69, 192).addBox(2.8724F, 7.3112F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(208, 174).addBox(2.9012F, 7.34F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(73, 192).addBox(2.8724F, 8.1272F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(144, 177).addBox(3.5636F, 7.652F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(77, 192).addBox(3.1556F, 7.6232F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(241, 180).addBox(3.1844F, 7.652F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(81, 192).addBox(-2.8576F, 7.3112F, 2.9F, 0.7776F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(211, 174).addBox(-2.1088F, 7.34F, 2.9F, 0.0576F, 0.8736F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(85, 192).addBox(-2.8F, 8.1272F, 2.9F, 0.72F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(147, 177).addBox(-2.7712F, 7.652F, 2.9F, 0.0576F, 0.5616F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(89, 192).addBox(-2.8F, 7.6232F, 2.9F, 0.4368F, 0.0576F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(244, 180).addBox(-2.392F, 7.652F, 2.9F, 0.0576F, 0.2832F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(92, 202).addBox(2.15F, 6.9F, 2.9F, 0.8F, 0.7F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(251, 48).addBox(3.12F, 8.6F, -3.4F, 0.56F, 1.1F, 0.6F, new CubeDeformation(0.0F))
		.texOffs(97, 202).addBox(-2.95F, 6.9F, 2.9F, 0.8F, 0.7F, 0.64F, new CubeDeformation(0.0F))
		.texOffs(221, 200).addBox(-3.68F, 8.6F, -3.4F, 0.56F, 1.1F, 0.6F, new CubeDeformation(0.0F))
		.texOffs(237, 198).addBox(-0.5F, 5.0F, -3.35F, 1.0F, 1.8F, 0.6F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition refined_side_apron_R_264_r1 = body.addOrReplaceChild("refined_side_apron_R_264_r1", CubeListBuilder.create().texOffs(176, 192).addBox(-0.28F, -1.2F, -1.9F, 0.56F, 2.4F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0F, 10.7F, -0.05F, 0.0F, 0.0F, 0.1745F));

		PartDefinition refined_back_plate_R_261_r1 = body.addOrReplaceChild("refined_back_plate_R_261_r1", CubeListBuilder.create().texOffs(194, 192).addBox(-0.9F, -2.725F, -0.26F, 1.8F, 5.45F, 0.52F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.6F, 4.175F, 3.11F, 0.0F, 0.0F, 0.1047F));

		PartDefinition refined_breastplate_return_R_260_r1 = body.addOrReplaceChild("refined_breastplate_return_R_260_r1", CubeListBuilder.create().texOffs(232, 192).addBox(-0.25F, -1.75F, -0.35F, 0.5F, 3.5F, 0.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.45F, 3.85F, -2.6F, 0.0F, 0.0F, -0.0873F));

		PartDefinition refined_pectorals_clasp_R_259_r1 = body.addOrReplaceChild("refined_pectorals_clasp_R_259_r1", CubeListBuilder.create().texOffs(69, 200).addBox(-0.65F, -0.75F, -0.275F, 1.3F, 1.5F, 0.55F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.95F, 3.85F, -3.275F, 0.0F, 0.0F, 0.1745F));

		PartDefinition refined_side_apron_L_258_r1 = body.addOrReplaceChild("refined_side_apron_L_258_r1", CubeListBuilder.create().texOffs(165, 192).addBox(-0.28F, -1.2F, -1.9F, 0.56F, 2.4F, 3.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, 10.7F, -0.05F, 0.0F, 0.0F, -0.1745F));

		PartDefinition refined_back_plate_L_255_r1 = body.addOrReplaceChild("refined_back_plate_L_255_r1", CubeListBuilder.create().texOffs(187, 192).addBox(-0.9F, -2.725F, -0.26F, 1.8F, 5.45F, 0.52F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.6F, 4.175F, 3.11F, 0.0F, 0.0F, -0.1047F));

		PartDefinition refined_breastplate_return_L_254_r1 = body.addOrReplaceChild("refined_breastplate_return_L_254_r1", CubeListBuilder.create().texOffs(227, 192).addBox(-0.25F, -1.75F, -0.35F, 0.5F, 3.5F, 0.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.45F, 3.85F, -2.6F, 0.0F, 0.0F, 0.0873F));

		PartDefinition refined_pectorals_clasp_L_253_r1 = body.addOrReplaceChild("refined_pectorals_clasp_L_253_r1", CubeListBuilder.create().texOffs(63, 200).addBox(-0.65F, -0.75F, -0.275F, 1.3F, 1.5F, 0.55F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.95F, 3.85F, -3.275F, 0.0F, 0.0F, -0.1745F));

		PartDefinition right_arm = partdefinition.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(234, 61).addBox(-2.3243F, -2.0F, -2.65F, 4.6486F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(234, 70).addBox(-2.4328F, -1.5143F, -2.65F, 4.8657F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(228, 87).addBox(-2.5415F, -1.0286F, -2.65F, 5.0829F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(186, 95).addBox(-2.65F, -0.5429F, -2.65F, 5.3F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(210, 95).addBox(-2.5415F, -0.0571F, -2.65F, 5.0829F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(233, 95).addBox(-2.4328F, 0.4286F, -2.65F, 4.8657F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(1, 103).addBox(-2.3243F, 0.9143F, -2.65F, 4.6486F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(97, 52).addBox(-2.8171F, -1.5F, -2.9F, 5.6342F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(122, 52).addBox(-2.8723F, -1.2875F, -2.9F, 5.7447F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(148, 52).addBox(-2.8723F, -1.075F, -2.9F, 5.7447F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(174, 52).addBox(-2.8171F, -0.8625F, -2.9F, 5.6342F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(199, 52).addBox(-2.9171F, -0.8F, -2.9F, 5.8342F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(225, 52).addBox(-2.9723F, -0.5875F, -2.9F, 5.9447F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(1, 61).addBox(-2.9723F, -0.375F, -2.9F, 5.9447F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(27, 61).addBox(-2.9171F, -0.1625F, -2.9F, 5.8342F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(53, 61).addBox(-3.0172F, -0.1F, -2.9F, 6.0343F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(79, 61).addBox(-3.0724F, 0.1125F, -2.9F, 6.1448F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(105, 61).addBox(-3.0724F, 0.325F, -2.9F, 6.1448F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(131, 61).addBox(-3.0172F, 0.5375F, -2.9F, 6.0343F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(181, 180).addBox(2.03F, -0.57F, -3.01F, 0.34F, 0.34F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(130, 183).addBox(2.1014F, -0.4986F, -3.07F, 0.1972F, 0.1972F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(184, 180).addBox(-2.37F, -0.57F, -3.01F, 0.34F, 0.34F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(133, 183).addBox(-2.2986F, -0.4986F, -3.07F, 0.1972F, 0.1972F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(86, 186).addBox(-0.008F, -0.654F, -3.03F, 1.458F, 0.108F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(173, 156).addBox(0.046F, -0.6F, -3.03F, 0.108F, 1.638F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(92, 186).addBox(-0.008F, 0.876F, -3.03F, 1.35F, 0.108F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(175, 166).addBox(1.288F, -0.015F, -3.03F, 0.108F, 1.053F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(97, 186).addBox(0.523F, -0.069F, -3.03F, 0.819F, 0.108F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(156, 177).addBox(0.577F, -0.015F, -3.03F, 0.108F, 0.531F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(239, 1).addBox(-1.5834F, 1.3F, -1.9F, 3.1667F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(158, 135).addBox(-1.71F, 1.9333F, -1.9F, 3.42F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(175, 135).addBox(-1.8366F, 2.5667F, -1.9F, 3.6733F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(192, 135).addBox(-1.8366F, 3.2F, -1.9F, 3.6733F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(209, 135).addBox(-1.71F, 3.8333F, -1.9F, 3.42F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(226, 135).addBox(-1.5834F, 4.4667F, -1.9F, 3.1667F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(160, 186).addBox(-0.1946F, 1.9502F, -2.05F, 1.3446F, 0.0996F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(46, 162).addBox(-0.1448F, 2.0F, -2.05F, 0.0996F, 1.5106F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(165, 186).addBox(-0.1946F, 3.3612F, -2.05F, 1.245F, 0.0996F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(201, 166).addBox(1.0006F, 2.5395F, -2.05F, 0.0996F, 0.9711F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(170, 186).addBox(0.2951F, 2.4897F, -2.05F, 0.7553F, 0.0996F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(99, 180).addBox(0.3449F, 2.5395F, -2.05F, 0.0996F, 0.4897F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(135, 156).addBox(-1.015F, 4.335F, -2.05F, 2.03F, 2.03F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(212, 166).addBox(-0.3F, 6.5F, -2.23F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F))
		.texOffs(52, 162).addBox(-0.6F, 4.75F, -2.32F, 1.2F, 1.2F, 0.4F, new CubeDeformation(0.0F))
		.texOffs(43, 119).addBox(-1.7243F, 6.25F, -2.35F, 3.4486F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(62, 119).addBox(-1.8328F, 6.7357F, -2.35F, 3.6657F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(81, 119).addBox(-1.9415F, 7.2214F, -2.35F, 3.8829F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(101, 119).addBox(-2.05F, 7.7071F, -2.35F, 4.1F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(121, 119).addBox(-1.9415F, 8.1929F, -2.35F, 3.8829F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(141, 119).addBox(-1.8328F, 8.6786F, -2.35F, 3.6657F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(160, 119).addBox(-1.7243F, 9.1643F, -2.35F, 3.4486F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(67, 156).addBox(1.52F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(70, 156).addBox(0.97F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(73, 156).addBox(0.42F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(76, 156).addBox(-0.13F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(79, 156).addBox(-0.68F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(82, 156).addBox(-1.23F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(85, 156).addBox(-1.78F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(205, 111).addBox(-2.18F, 6.35F, -2.48F, 4.36F, 0.24F, 4.96F, new CubeDeformation(0.0F))
		.texOffs(226, 111).addBox(-2.18F, 9.25F, -2.48F, 4.36F, 0.24F, 4.96F, new CubeDeformation(0.0F))
		.texOffs(191, 143).addBox(-1.685F, 9.65F, -1.85F, 3.37F, 0.25F, 3.7F, new CubeDeformation(0.0F))
		.texOffs(208, 143).addBox(-1.75F, 9.9F, -1.85F, 3.5F, 0.25F, 3.7F, new CubeDeformation(0.0F))
		.texOffs(225, 143).addBox(-1.685F, 10.15F, -1.85F, 3.37F, 0.25F, 3.7F, new CubeDeformation(0.0F))
		.texOffs(41, 200).addBox(-1.9F, -1.1F, 2.75F, 3.8F, 1.5F, 0.6F, new CubeDeformation(0.0F))
		.texOffs(240, 203).addBox(-1.9F, -1.1F, -3.26F, 3.8F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(29, 201).addBox(1.35F, 9.68F, -1.85F, 0.6F, 0.75F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(141, 201).addBox(-1.95F, 9.68F, -1.85F, 0.6F, 0.75F, 0.8F, new CubeDeformation(0.0F)), PartPose.offset(6.0F, 2.0F, 0.0F));

		PartDefinition refined_thumb_cap_L_116_r1 = right_arm.addOrReplaceChild("refined_thumb_cap_L_116_r1", CubeListBuilder.create().texOffs(251, 58).addBox(-0.29F, -0.3F, -0.05F, 0.58F, 0.6F, 0.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0591F, 9.8751F, -1.35F, 0.0F, 1.5708F, 1.1345F));

		PartDefinition refined_finger_nail_L3_115_r1 = right_arm.addOrReplaceChild("refined_finger_nail_L3_115_r1", CubeListBuilder.create().texOffs(202, 204).addBox(-0.2F, -0.06F, -0.125F, 0.4F, 0.12F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.68F, 10.5F, 1.2F, 0.0F, 1.5708F, -1.5708F));

		PartDefinition refined_knuckle_cap_L3_114_r1 = right_arm.addOrReplaceChild("refined_knuckle_cap_L3_114_r1", CubeListBuilder.create().texOffs(5, 205).addBox(-0.23F, -0.17F, -0.125F, 0.46F, 0.34F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.99F, 10.7F, 1.2F, 0.0F, 1.5708F, 0.0F));

		PartDefinition refined_finger_nail_L2_113_r1 = right_arm.addOrReplaceChild("refined_finger_nail_L2_113_r1", CubeListBuilder.create().texOffs(198, 204).addBox(-0.2F, -0.06F, -0.125F, 0.4F, 0.12F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.68F, 10.5F, 0.38F, 0.0F, 1.5708F, -1.5708F));

		PartDefinition refined_knuckle_cap_L2_112_r1 = right_arm.addOrReplaceChild("refined_knuckle_cap_L2_112_r1", CubeListBuilder.create().texOffs(1, 205).addBox(-0.23F, -0.17F, -0.125F, 0.46F, 0.34F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.99F, 10.7F, 0.38F, 0.0F, 1.5708F, 0.0F));

		PartDefinition refined_finger_nail_L1_111_r1 = right_arm.addOrReplaceChild("refined_finger_nail_L1_111_r1", CubeListBuilder.create().texOffs(194, 204).addBox(-0.2F, -0.06F, -0.125F, 0.4F, 0.12F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.68F, 10.5F, -0.44F, 0.0F, 1.5708F, -1.5708F));

		PartDefinition refined_knuckle_cap_L1_110_r1 = right_arm.addOrReplaceChild("refined_knuckle_cap_L1_110_r1", CubeListBuilder.create().texOffs(226, 204).addBox(-0.23F, -0.17F, -0.125F, 0.46F, 0.34F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.99F, 10.7F, -0.44F, 0.0F, 1.5708F, 0.0F));

		PartDefinition refined_finger_nail_L0_109_r1 = right_arm.addOrReplaceChild("refined_finger_nail_L0_109_r1", CubeListBuilder.create().texOffs(190, 204).addBox(-0.2F, -0.06F, -0.125F, 0.4F, 0.12F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.68F, 10.5F, -1.26F, 0.0F, 1.5708F, -1.5708F));

		PartDefinition refined_knuckle_cap_L0_108_r1 = right_arm.addOrReplaceChild("refined_knuckle_cap_L0_108_r1", CubeListBuilder.create().texOffs(222, 204).addBox(-0.23F, -0.17F, -0.125F, 0.46F, 0.34F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.99F, 10.7F, -1.26F, 0.0F, 1.5708F, 0.0F));

		PartDefinition refined_bracer_rear_lower_L_105_r1 = right_arm.addOrReplaceChild("refined_bracer_rear_lower_L_105_r1", CubeListBuilder.create().texOffs(121, 201).addBox(-1.75F, -0.6F, -0.225F, 3.5F, 1.2F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.55F, 2.595F, 0.0F, 0.0F, 0.0698F));

		PartDefinition refined_bracer_rear_upper_L_104_r1 = right_arm.addOrReplaceChild("refined_bracer_rear_upper_L_104_r1", CubeListBuilder.create().texOffs(82, 200).addBox(-1.75F, -0.675F, -0.225F, 3.5F, 1.35F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 7.125F, 2.575F, 0.0F, 0.0F, -0.0698F));

		PartDefinition refined_elbow_axle_L1_103_r1 = right_arm.addOrReplaceChild("refined_elbow_axle_L1_103_r1", CubeListBuilder.create().texOffs(73, 195).addBox(-0.3F, -0.6F, -0.65F, 0.6F, 1.2F, 1.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.85F, 5.4F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition refined_elbow_axle_L-1_102_r1 = right_arm.addOrReplaceChild("refined_elbow_axle_L-1_102_r1", CubeListBuilder.create().texOffs(67, 195).addBox(-0.3F, -0.6F, -0.65F, 0.6F, 1.2F, 1.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.85F, 5.4F, 0.0F, 0.0F, 0.7854F, 0.0F));

		PartDefinition refined_shoulder_crown_L_99_r1 = right_arm.addOrReplaceChild("refined_shoulder_crown_L_99_r1", CubeListBuilder.create().texOffs(93, 192).addBox(-0.38F, -1.25F, -2.6F, 0.76F, 2.5F, 5.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.45F, -0.65F, 0.0F, 0.0F, 0.0F, -0.2094F));

		PartDefinition opposed_thumb_98_r1 = right_arm.addOrReplaceChild("opposed_thumb_98_r1", CubeListBuilder.create().texOffs(109, 156).addBox(-0.35F, -0.8F, -0.18F, 0.7F, 1.6F, 0.36F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.15F, 10.07F, -1.35F, 0.0F, 1.5708F, 1.1345F));

		PartDefinition worn_rivet_97_r1 = right_arm.addOrReplaceChild("worn_rivet_97_r1", CubeListBuilder.create().texOffs(71, 186).addBox(-0.0638F, -0.0638F, -0.045F, 0.1276F, 0.1276F, 0.09F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5554F, 11.166F, 1.2F, 0.0F, -1.5708F, 0.6981F));

		PartDefinition rivet_base_96_r1 = right_arm.addOrReplaceChild("rivet_base_96_r1", CubeListBuilder.create().texOffs(115, 183).addBox(-0.11F, -0.11F, -0.05F, 0.22F, 0.22F, 0.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5056F, 11.1242F, 1.2F, 0.0F, -1.5708F, 0.6981F));

		PartDefinition finger_curled_tip_95_r1 = right_arm.addOrReplaceChild("finger_curled_tip_95_r1", CubeListBuilder.create().texOffs(197, 156).addBox(-0.29F, -0.15F, -0.525F, 0.58F, 0.3F, 1.05F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.49F, 10.65F, 1.2F, 0.0F, 1.5708F, -1.5708F));

		PartDefinition finger_proximal_94_r1 = right_arm.addOrReplaceChild("finger_proximal_94_r1", CubeListBuilder.create().texOffs(171, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.3754F, 11.015F, 1.2F, 0.0F, 1.5708F, 0.8727F));

		PartDefinition finger_proximal_93_r1 = right_arm.addOrReplaceChild("finger_proximal_93_r1", CubeListBuilder.create().texOffs(166, 170).addBox(-0.33F, -0.1234F, -0.39F, 0.66F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 11.19F, 1.2F, 0.0F, 1.5708F, 0.0F));

		PartDefinition finger_proximal_92_r1 = right_arm.addOrReplaceChild("finger_proximal_92_r1", CubeListBuilder.create().texOffs(161, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3754F, 11.015F, 1.2F, 0.0F, 1.5708F, -0.8727F));

		PartDefinition worn_rivet_91_r1 = right_arm.addOrReplaceChild("worn_rivet_91_r1", CubeListBuilder.create().texOffs(68, 186).addBox(-0.0638F, -0.0638F, -0.045F, 0.1276F, 0.1276F, 0.09F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5554F, 11.166F, 0.38F, 0.0F, -1.5708F, 0.6981F));

		PartDefinition rivet_base_90_r1 = right_arm.addOrReplaceChild("rivet_base_90_r1", CubeListBuilder.create().texOffs(112, 183).addBox(-0.11F, -0.11F, -0.05F, 0.22F, 0.22F, 0.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5056F, 11.1242F, 0.38F, 0.0F, -1.5708F, 0.6981F));

		PartDefinition finger_curled_tip_89_r1 = right_arm.addOrReplaceChild("finger_curled_tip_89_r1", CubeListBuilder.create().texOffs(191, 156).addBox(-0.29F, -0.15F, -0.525F, 0.58F, 0.3F, 1.05F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.49F, 10.65F, 0.38F, 0.0F, 1.5708F, -1.5708F));

		PartDefinition finger_proximal_88_r1 = right_arm.addOrReplaceChild("finger_proximal_88_r1", CubeListBuilder.create().texOffs(156, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.3754F, 11.015F, 0.38F, 0.0F, 1.5708F, 0.8727F));

		PartDefinition finger_proximal_87_r1 = right_arm.addOrReplaceChild("finger_proximal_87_r1", CubeListBuilder.create().texOffs(151, 170).addBox(-0.33F, -0.1234F, -0.39F, 0.66F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 11.19F, 0.38F, 0.0F, 1.5708F, 0.0F));

		PartDefinition finger_proximal_86_r1 = right_arm.addOrReplaceChild("finger_proximal_86_r1", CubeListBuilder.create().texOffs(146, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3754F, 11.015F, 0.38F, 0.0F, 1.5708F, -0.8727F));

		PartDefinition worn_rivet_85_r1 = right_arm.addOrReplaceChild("worn_rivet_85_r1", CubeListBuilder.create().texOffs(65, 186).addBox(-0.0638F, -0.0638F, -0.045F, 0.1276F, 0.1276F, 0.09F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5554F, 11.166F, -0.44F, 0.0F, -1.5708F, 0.6981F));

		PartDefinition rivet_base_84_r1 = right_arm.addOrReplaceChild("rivet_base_84_r1", CubeListBuilder.create().texOffs(109, 183).addBox(-0.11F, -0.11F, -0.05F, 0.22F, 0.22F, 0.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5056F, 11.1242F, -0.44F, 0.0F, -1.5708F, 0.6981F));

		PartDefinition finger_curled_tip_83_r1 = right_arm.addOrReplaceChild("finger_curled_tip_83_r1", CubeListBuilder.create().texOffs(185, 156).addBox(-0.29F, -0.15F, -0.525F, 0.58F, 0.3F, 1.05F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.49F, 10.65F, -0.44F, 0.0F, 1.5708F, -1.5708F));

		PartDefinition finger_proximal_82_r1 = right_arm.addOrReplaceChild("finger_proximal_82_r1", CubeListBuilder.create().texOffs(141, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.3754F, 11.015F, -0.44F, 0.0F, 1.5708F, 0.8727F));

		PartDefinition finger_proximal_81_r1 = right_arm.addOrReplaceChild("finger_proximal_81_r1", CubeListBuilder.create().texOffs(136, 170).addBox(-0.33F, -0.1234F, -0.39F, 0.66F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 11.19F, -0.44F, 0.0F, 1.5708F, 0.0F));

		PartDefinition finger_proximal_80_r1 = right_arm.addOrReplaceChild("finger_proximal_80_r1", CubeListBuilder.create().texOffs(131, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3754F, 11.015F, -0.44F, 0.0F, 1.5708F, -0.8727F));

		PartDefinition worn_rivet_79_r1 = right_arm.addOrReplaceChild("worn_rivet_79_r1", CubeListBuilder.create().texOffs(62, 186).addBox(-0.0638F, -0.0638F, -0.045F, 0.1276F, 0.1276F, 0.09F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5554F, 11.166F, -1.26F, 0.0F, -1.5708F, 0.6981F));

		PartDefinition rivet_base_78_r1 = right_arm.addOrReplaceChild("rivet_base_78_r1", CubeListBuilder.create().texOffs(106, 183).addBox(-0.11F, -0.11F, -0.05F, 0.22F, 0.22F, 0.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5056F, 11.1242F, -1.26F, 0.0F, -1.5708F, 0.6981F));

		PartDefinition finger_curled_tip_77_r1 = right_arm.addOrReplaceChild("finger_curled_tip_77_r1", CubeListBuilder.create().texOffs(179, 156).addBox(-0.29F, -0.15F, -0.525F, 0.58F, 0.3F, 1.05F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.49F, 10.65F, -1.26F, 0.0F, 1.5708F, -1.5708F));

		PartDefinition finger_proximal_76_r1 = right_arm.addOrReplaceChild("finger_proximal_76_r1", CubeListBuilder.create().texOffs(126, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.3754F, 11.015F, -1.26F, 0.0F, 1.5708F, 0.8727F));

		PartDefinition finger_proximal_75_r1 = right_arm.addOrReplaceChild("finger_proximal_75_r1", CubeListBuilder.create().texOffs(121, 170).addBox(-0.33F, -0.1234F, -0.39F, 0.66F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 11.19F, -1.26F, 0.0F, 1.5708F, 0.0F));

		PartDefinition finger_proximal_74_r1 = right_arm.addOrReplaceChild("finger_proximal_74_r1", CubeListBuilder.create().texOffs(116, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3754F, 11.015F, -1.26F, 0.0F, 1.5708F, -0.8727F));

		PartDefinition palm_73_r1 = right_arm.addOrReplaceChild("palm_73_r1", CubeListBuilder.create().texOffs(242, 143).addBox(-1.6086F, -0.1813F, -0.29F, 3.2172F, 0.3625F, 0.58F, new CubeDeformation(0.0F))
		.texOffs(170, 150).addBox(-1.7028F, -0.5437F, -0.29F, 3.4057F, 0.3625F, 0.58F, new CubeDeformation(0.0F))
		.texOffs(154, 150).addBox(-1.7028F, -0.9063F, -0.29F, 3.4057F, 0.3625F, 0.58F, new CubeDeformation(0.0F))
		.texOffs(242, 135).addBox(-1.6086F, -1.2688F, -0.29F, 3.2172F, 0.3625F, 0.58F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.62F, 11.2188F, 0.0F, 0.0F, 1.5708F, 0.0F));

		PartDefinition faceted_joint_ring_49_r1 = right_arm.addOrReplaceChild("faceted_joint_ring_49_r1", CubeListBuilder.create().texOffs(247, 166).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0253F, 6.3753F, -2.005F, 0.0F, 0.0F, 5.4978F));

		PartDefinition faceted_joint_ring_48_r1 = right_arm.addOrReplaceChild("faceted_joint_ring_48_r1", CubeListBuilder.create().texOffs(242, 166).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.45F, 5.35F, -2.005F, 0.0F, 0.0F, 4.7124F));

		PartDefinition faceted_joint_ring_47_r1 = right_arm.addOrReplaceChild("faceted_joint_ring_47_r1", CubeListBuilder.create().texOffs(237, 166).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0253F, 4.3247F, -2.005F, 0.0F, 0.0F, 3.927F));

		PartDefinition faceted_joint_ring_46_r1 = right_arm.addOrReplaceChild("faceted_joint_ring_46_r1", CubeListBuilder.create().texOffs(232, 166).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.9F, -2.005F, 0.0F, 0.0F, 3.1416F));

		PartDefinition faceted_joint_ring_45_r1 = right_arm.addOrReplaceChild("faceted_joint_ring_45_r1", CubeListBuilder.create().texOffs(227, 166).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0253F, 4.3247F, -2.005F, 0.0F, 0.0F, 2.3562F));

		PartDefinition faceted_joint_ring_44_r1 = right_arm.addOrReplaceChild("faceted_joint_ring_44_r1", CubeListBuilder.create().texOffs(222, 166).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.45F, 5.35F, -2.005F, 0.0F, 0.0F, 1.5708F));

		PartDefinition faceted_joint_ring_43_r1 = right_arm.addOrReplaceChild("faceted_joint_ring_43_r1", CubeListBuilder.create().texOffs(217, 166).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0253F, 6.3753F, -2.005F, 0.0F, 0.0F, 0.7854F));

		PartDefinition left_arm = partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(23, 103).addBox(-2.3243F, -2.0F, -2.65F, 4.6486F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(45, 103).addBox(-2.4328F, -1.5143F, -2.65F, 4.8657F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(68, 103).addBox(-2.5415F, -1.0286F, -2.65F, 5.0829F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(91, 103).addBox(-2.65F, -0.5429F, -2.65F, 5.3F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(115, 103).addBox(-2.5415F, -0.0571F, -2.65F, 5.0829F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(138, 103).addBox(-2.4328F, 0.4286F, -2.65F, 4.8657F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(161, 103).addBox(-2.3243F, 0.9143F, -2.65F, 4.6486F, 0.4857F, 5.3F, new CubeDeformation(0.0F))
		.texOffs(157, 61).addBox(-2.8171F, -1.5F, -2.9F, 5.6342F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(182, 61).addBox(-2.8723F, -1.2875F, -2.9F, 5.7447F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(208, 61).addBox(-2.8723F, -1.075F, -2.9F, 5.7447F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(1, 70).addBox(-2.8171F, -0.8625F, -2.9F, 5.6342F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(26, 70).addBox(-2.9171F, -0.8F, -2.9F, 5.8342F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(52, 70).addBox(-2.9723F, -0.5875F, -2.9F, 5.9447F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(78, 70).addBox(-2.9723F, -0.375F, -2.9F, 5.9447F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(104, 70).addBox(-2.9171F, -0.1625F, -2.9F, 5.8342F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(130, 70).addBox(-3.0172F, -0.1F, -2.9F, 6.0343F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(156, 70).addBox(-3.0724F, 0.1125F, -2.9F, 6.1448F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(182, 70).addBox(-3.0724F, 0.325F, -2.9F, 6.1448F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(208, 70).addBox(-3.0172F, 0.5375F, -2.9F, 6.0343F, 0.2125F, 5.8F, new CubeDeformation(0.0F))
		.texOffs(187, 180).addBox(2.03F, -0.57F, -3.01F, 0.34F, 0.34F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(136, 183).addBox(2.1014F, -0.4986F, -3.07F, 0.1972F, 0.1972F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(190, 180).addBox(-2.37F, -0.57F, -3.01F, 0.34F, 0.34F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(139, 183).addBox(-2.2986F, -0.4986F, -3.07F, 0.1972F, 0.1972F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(101, 186).addBox(-0.008F, -0.654F, -3.03F, 1.458F, 0.108F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(176, 156).addBox(0.046F, -0.6F, -3.03F, 0.108F, 1.638F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(107, 186).addBox(-0.008F, 0.876F, -3.03F, 1.35F, 0.108F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(178, 166).addBox(1.288F, -0.015F, -3.03F, 0.108F, 1.053F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(112, 186).addBox(0.523F, -0.069F, -3.03F, 0.819F, 0.108F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(159, 177).addBox(0.577F, -0.015F, -3.03F, 0.108F, 0.531F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(1, 143).addBox(-1.5834F, 1.3F, -1.9F, 3.1667F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(17, 143).addBox(-1.71F, 1.9333F, -1.9F, 3.42F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(34, 143).addBox(-1.8366F, 2.5667F, -1.9F, 3.6733F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(51, 143).addBox(-1.8366F, 3.2F, -1.9F, 3.6733F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(68, 143).addBox(-1.71F, 3.8333F, -1.9F, 3.42F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(85, 143).addBox(-1.5834F, 4.4667F, -1.9F, 3.1667F, 0.6333F, 3.8F, new CubeDeformation(0.0F))
		.texOffs(174, 186).addBox(-0.1946F, 1.9502F, -2.05F, 1.3446F, 0.0996F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(49, 162).addBox(-0.1448F, 2.0F, -2.05F, 0.0996F, 1.5106F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(179, 186).addBox(-0.1946F, 3.3612F, -2.05F, 1.245F, 0.0996F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(204, 166).addBox(1.0006F, 2.5395F, -2.05F, 0.0996F, 0.9711F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(184, 186).addBox(0.2951F, 2.4897F, -2.05F, 0.7553F, 0.0996F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(102, 180).addBox(0.3449F, 2.5395F, -2.05F, 0.0996F, 0.4897F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(142, 156).addBox(-1.015F, 4.335F, -2.05F, 2.03F, 2.03F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(252, 166).addBox(-0.3F, 6.5F, -2.23F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F))
		.texOffs(58, 162).addBox(-0.6F, 4.75F, -2.32F, 1.2F, 1.2F, 0.4F, new CubeDeformation(0.0F))
		.texOffs(179, 119).addBox(-1.7243F, 6.25F, -2.35F, 3.4486F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(198, 119).addBox(-1.8328F, 6.7357F, -2.35F, 3.6657F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(217, 119).addBox(-1.9415F, 7.2214F, -2.35F, 3.8829F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(237, 119).addBox(-2.05F, 7.7071F, -2.35F, 4.1F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(1, 127).addBox(-1.9415F, 8.1929F, -2.35F, 3.8829F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(21, 127).addBox(-1.8328F, 8.6786F, -2.35F, 3.6657F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(40, 127).addBox(-1.7243F, 9.1643F, -2.35F, 3.4486F, 0.4857F, 4.7F, new CubeDeformation(0.0F))
		.texOffs(88, 156).addBox(1.52F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(91, 156).addBox(0.97F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(94, 156).addBox(0.42F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(97, 156).addBox(-0.13F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(100, 156).addBox(-0.68F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(103, 156).addBox(-1.23F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(106, 156).addBox(-1.78F, 6.6F, -2.5F, 0.13F, 2.65F, 0.22F, new CubeDeformation(0.0F))
		.texOffs(1, 119).addBox(-2.18F, 6.35F, -2.48F, 4.36F, 0.24F, 4.96F, new CubeDeformation(0.0F))
		.texOffs(22, 119).addBox(-2.18F, 9.25F, -2.48F, 4.36F, 0.24F, 4.96F, new CubeDeformation(0.0F))
		.texOffs(1, 150).addBox(-1.685F, 9.65F, -1.85F, 3.37F, 0.25F, 3.7F, new CubeDeformation(0.0F))
		.texOffs(18, 150).addBox(-1.75F, 9.9F, -1.85F, 3.5F, 0.25F, 3.7F, new CubeDeformation(0.0F))
		.texOffs(35, 150).addBox(-1.685F, 10.15F, -1.85F, 3.37F, 0.25F, 3.7F, new CubeDeformation(0.0F))
		.texOffs(52, 200).addBox(-1.9F, -1.1F, 2.75F, 3.8F, 1.5F, 0.6F, new CubeDeformation(0.0F))
		.texOffs(75, 204).addBox(-1.9F, -1.1F, -3.26F, 3.8F, 0.5F, 0.5F, new CubeDeformation(0.0F))
		.texOffs(146, 201).addBox(1.35F, 9.68F, -1.85F, 0.6F, 0.75F, 0.8F, new CubeDeformation(0.0F))
		.texOffs(151, 201).addBox(-1.95F, 9.68F, -1.85F, 0.6F, 0.75F, 0.8F, new CubeDeformation(0.0F)), PartPose.offset(-6.0F, 2.0F, 0.0F));

		PartDefinition refined_thumb_cap_R_116_r1 = left_arm.addOrReplaceChild("refined_thumb_cap_R_116_r1", CubeListBuilder.create().texOffs(251, 203).addBox(-0.29F, -0.3F, -0.05F, 0.58F, 0.6F, 0.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0591F, 9.8751F, -1.35F, 0.0F, -1.5708F, -1.1345F));

		PartDefinition refined_finger_nail_R3_115_r1 = left_arm.addOrReplaceChild("refined_finger_nail_R3_115_r1", CubeListBuilder.create().texOffs(218, 204).addBox(-0.2F, -0.06F, -0.125F, 0.4F, 0.12F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.68F, 10.5F, -1.2F, 0.0F, -1.5708F, 1.5708F));

		PartDefinition refined_knuckle_cap_R3_114_r1 = left_arm.addOrReplaceChild("refined_knuckle_cap_R3_114_r1", CubeListBuilder.create().texOffs(21, 205).addBox(-0.23F, -0.17F, -0.125F, 0.46F, 0.34F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.99F, 10.7F, -1.2F, 0.0F, -1.5708F, 0.0F));

		PartDefinition refined_finger_nail_R2_113_r1 = left_arm.addOrReplaceChild("refined_finger_nail_R2_113_r1", CubeListBuilder.create().texOffs(214, 204).addBox(-0.2F, -0.06F, -0.125F, 0.4F, 0.12F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.68F, 10.5F, -0.38F, 0.0F, -1.5708F, 1.5708F));

		PartDefinition refined_knuckle_cap_R2_112_r1 = left_arm.addOrReplaceChild("refined_knuckle_cap_R2_112_r1", CubeListBuilder.create().texOffs(17, 205).addBox(-0.23F, -0.17F, -0.125F, 0.46F, 0.34F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.99F, 10.7F, -0.38F, 0.0F, -1.5708F, 0.0F));

		PartDefinition refined_finger_nail_R1_111_r1 = left_arm.addOrReplaceChild("refined_finger_nail_R1_111_r1", CubeListBuilder.create().texOffs(210, 204).addBox(-0.2F, -0.06F, -0.125F, 0.4F, 0.12F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.68F, 10.5F, 0.44F, 0.0F, -1.5708F, 1.5708F));

		PartDefinition refined_knuckle_cap_R1_110_r1 = left_arm.addOrReplaceChild("refined_knuckle_cap_R1_110_r1", CubeListBuilder.create().texOffs(13, 205).addBox(-0.23F, -0.17F, -0.125F, 0.46F, 0.34F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.99F, 10.7F, 0.44F, 0.0F, -1.5708F, 0.0F));

		PartDefinition refined_finger_nail_R0_109_r1 = left_arm.addOrReplaceChild("refined_finger_nail_R0_109_r1", CubeListBuilder.create().texOffs(206, 204).addBox(-0.2F, -0.06F, -0.125F, 0.4F, 0.12F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.68F, 10.5F, 1.26F, 0.0F, -1.5708F, 1.5708F));

		PartDefinition refined_knuckle_cap_R0_108_r1 = left_arm.addOrReplaceChild("refined_knuckle_cap_R0_108_r1", CubeListBuilder.create().texOffs(9, 205).addBox(-0.23F, -0.17F, -0.125F, 0.46F, 0.34F, 0.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.99F, 10.7F, 1.26F, 0.0F, -1.5708F, 0.0F));

		PartDefinition refined_bracer_rear_lower_R_105_r1 = left_arm.addOrReplaceChild("refined_bracer_rear_lower_R_105_r1", CubeListBuilder.create().texOffs(131, 201).addBox(-1.75F, -0.6F, -0.225F, 3.5F, 1.2F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.55F, 2.595F, 0.0F, 0.0F, 0.0698F));

		PartDefinition refined_bracer_rear_upper_R_104_r1 = left_arm.addOrReplaceChild("refined_bracer_rear_upper_R_104_r1", CubeListBuilder.create().texOffs(211, 200).addBox(-1.75F, -0.675F, -0.225F, 3.5F, 1.35F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 7.125F, 2.575F, 0.0F, 0.0F, -0.0698F));

		PartDefinition refined_elbow_axle_R1_103_r1 = left_arm.addOrReplaceChild("refined_elbow_axle_R1_103_r1", CubeListBuilder.create().texOffs(85, 195).addBox(-0.3F, -0.6F, -0.65F, 0.6F, 1.2F, 1.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.85F, 5.4F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition refined_elbow_axle_R-1_102_r1 = left_arm.addOrReplaceChild("refined_elbow_axle_R-1_102_r1", CubeListBuilder.create().texOffs(79, 195).addBox(-0.3F, -0.6F, -0.65F, 0.6F, 1.2F, 1.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.85F, 5.4F, 0.0F, 0.0F, 0.7854F, 0.0F));

		PartDefinition refined_shoulder_crown_R_99_r1 = left_arm.addOrReplaceChild("refined_shoulder_crown_R_99_r1", CubeListBuilder.create().texOffs(107, 192).addBox(-0.38F, -1.25F, -2.6F, 0.76F, 2.5F, 5.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.45F, -0.65F, 0.0F, 0.0F, 0.0F, 0.2094F));

		PartDefinition opposed_thumb_98_r2 = left_arm.addOrReplaceChild("opposed_thumb_98_r2", CubeListBuilder.create().texOffs(115, 156).addBox(-0.35F, -0.8F, -0.18F, 0.7F, 1.6F, 0.36F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.15F, 10.07F, -1.35F, 0.0F, -1.5708F, -1.1345F));

		PartDefinition worn_rivet_97_r2 = left_arm.addOrReplaceChild("worn_rivet_97_r2", CubeListBuilder.create().texOffs(83, 186).addBox(-0.0638F, -0.0638F, -0.045F, 0.1276F, 0.1276F, 0.09F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5554F, 11.166F, -1.2F, 0.0F, 1.5708F, -0.6981F));

		PartDefinition rivet_base_96_r2 = left_arm.addOrReplaceChild("rivet_base_96_r2", CubeListBuilder.create().texOffs(127, 183).addBox(-0.11F, -0.11F, -0.05F, 0.22F, 0.22F, 0.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5056F, 11.1242F, -1.2F, 0.0F, 1.5708F, -0.6981F));

		PartDefinition finger_curled_tip_95_r2 = left_arm.addOrReplaceChild("finger_curled_tip_95_r2", CubeListBuilder.create().texOffs(221, 156).addBox(-0.29F, -0.15F, -0.525F, 0.58F, 0.3F, 1.05F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.49F, 10.65F, -1.2F, 0.0F, -1.5708F, 1.5708F));

		PartDefinition finger_proximal_94_r2 = left_arm.addOrReplaceChild("finger_proximal_94_r2", CubeListBuilder.create().texOffs(231, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3754F, 11.015F, -1.2F, 0.0F, -1.5708F, -0.8727F));

		PartDefinition finger_proximal_93_r2 = left_arm.addOrReplaceChild("finger_proximal_93_r2", CubeListBuilder.create().texOffs(226, 170).addBox(-0.33F, -0.1234F, -0.39F, 0.66F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 11.19F, -1.2F, 0.0F, -1.5708F, 0.0F));

		PartDefinition finger_proximal_92_r2 = left_arm.addOrReplaceChild("finger_proximal_92_r2", CubeListBuilder.create().texOffs(221, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.3754F, 11.015F, -1.2F, 0.0F, -1.5708F, 0.8727F));

		PartDefinition worn_rivet_91_r2 = left_arm.addOrReplaceChild("worn_rivet_91_r2", CubeListBuilder.create().texOffs(80, 186).addBox(-0.0638F, -0.0638F, -0.045F, 0.1276F, 0.1276F, 0.09F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5554F, 11.166F, -0.38F, 0.0F, 1.5708F, -0.6981F));

		PartDefinition rivet_base_90_r2 = left_arm.addOrReplaceChild("rivet_base_90_r2", CubeListBuilder.create().texOffs(124, 183).addBox(-0.11F, -0.11F, -0.05F, 0.22F, 0.22F, 0.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5056F, 11.1242F, -0.38F, 0.0F, 1.5708F, -0.6981F));

		PartDefinition finger_curled_tip_89_r2 = left_arm.addOrReplaceChild("finger_curled_tip_89_r2", CubeListBuilder.create().texOffs(215, 156).addBox(-0.29F, -0.15F, -0.525F, 0.58F, 0.3F, 1.05F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.49F, 10.65F, -0.38F, 0.0F, -1.5708F, 1.5708F));

		PartDefinition finger_proximal_88_r2 = left_arm.addOrReplaceChild("finger_proximal_88_r2", CubeListBuilder.create().texOffs(216, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3754F, 11.015F, -0.38F, 0.0F, -1.5708F, -0.8727F));

		PartDefinition finger_proximal_87_r2 = left_arm.addOrReplaceChild("finger_proximal_87_r2", CubeListBuilder.create().texOffs(211, 170).addBox(-0.33F, -0.1234F, -0.39F, 0.66F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 11.19F, -0.38F, 0.0F, -1.5708F, 0.0F));

		PartDefinition finger_proximal_86_r2 = left_arm.addOrReplaceChild("finger_proximal_86_r2", CubeListBuilder.create().texOffs(206, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.3754F, 11.015F, -0.38F, 0.0F, -1.5708F, 0.8727F));

		PartDefinition worn_rivet_85_r2 = left_arm.addOrReplaceChild("worn_rivet_85_r2", CubeListBuilder.create().texOffs(77, 186).addBox(-0.0638F, -0.0638F, -0.045F, 0.1276F, 0.1276F, 0.09F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5554F, 11.166F, 0.44F, 0.0F, 1.5708F, -0.6981F));

		PartDefinition rivet_base_84_r2 = left_arm.addOrReplaceChild("rivet_base_84_r2", CubeListBuilder.create().texOffs(121, 183).addBox(-0.11F, -0.11F, -0.05F, 0.22F, 0.22F, 0.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5056F, 11.1242F, 0.44F, 0.0F, 1.5708F, -0.6981F));

		PartDefinition finger_curled_tip_83_r2 = left_arm.addOrReplaceChild("finger_curled_tip_83_r2", CubeListBuilder.create().texOffs(209, 156).addBox(-0.29F, -0.15F, -0.525F, 0.58F, 0.3F, 1.05F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.49F, 10.65F, 0.44F, 0.0F, -1.5708F, 1.5708F));

		PartDefinition finger_proximal_82_r2 = left_arm.addOrReplaceChild("finger_proximal_82_r2", CubeListBuilder.create().texOffs(201, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3754F, 11.015F, 0.44F, 0.0F, -1.5708F, -0.8727F));

		PartDefinition finger_proximal_81_r2 = left_arm.addOrReplaceChild("finger_proximal_81_r2", CubeListBuilder.create().texOffs(196, 170).addBox(-0.33F, -0.1234F, -0.39F, 0.66F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 11.19F, 0.44F, 0.0F, -1.5708F, 0.0F));

		PartDefinition finger_proximal_80_r2 = left_arm.addOrReplaceChild("finger_proximal_80_r2", CubeListBuilder.create().texOffs(191, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.3754F, 11.015F, 0.44F, 0.0F, -1.5708F, 0.8727F));

		PartDefinition worn_rivet_79_r2 = left_arm.addOrReplaceChild("worn_rivet_79_r2", CubeListBuilder.create().texOffs(74, 186).addBox(-0.0638F, -0.0638F, -0.045F, 0.1276F, 0.1276F, 0.09F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5554F, 11.166F, 1.26F, 0.0F, 1.5708F, -0.6981F));

		PartDefinition rivet_base_78_r2 = left_arm.addOrReplaceChild("rivet_base_78_r2", CubeListBuilder.create().texOffs(118, 183).addBox(-0.11F, -0.11F, -0.05F, 0.22F, 0.22F, 0.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5056F, 11.1242F, 1.26F, 0.0F, 1.5708F, -0.6981F));

		PartDefinition finger_curled_tip_77_r2 = left_arm.addOrReplaceChild("finger_curled_tip_77_r2", CubeListBuilder.create().texOffs(203, 156).addBox(-0.29F, -0.15F, -0.525F, 0.58F, 0.3F, 1.05F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.49F, 10.65F, 1.26F, 0.0F, -1.5708F, 1.5708F));

		PartDefinition finger_proximal_76_r2 = left_arm.addOrReplaceChild("finger_proximal_76_r2", CubeListBuilder.create().texOffs(186, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3754F, 11.015F, 1.26F, 0.0F, -1.5708F, -0.8727F));

		PartDefinition finger_proximal_75_r2 = left_arm.addOrReplaceChild("finger_proximal_75_r2", CubeListBuilder.create().texOffs(181, 170).addBox(-0.33F, -0.1234F, -0.39F, 0.66F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 11.19F, 1.26F, 0.0F, -1.5708F, 0.0F));

		PartDefinition finger_proximal_74_r2 = left_arm.addOrReplaceChild("finger_proximal_74_r2", CubeListBuilder.create().texOffs(176, 170).addBox(-0.2728F, -0.1234F, -0.39F, 0.5456F, 0.2467F, 0.78F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.3754F, 11.015F, 1.26F, 0.0F, -1.5708F, 0.8727F));

		PartDefinition palm_73_r2 = left_arm.addOrReplaceChild("palm_73_r2", CubeListBuilder.create().texOffs(233, 150).addBox(-1.6086F, -0.1813F, -0.29F, 3.2172F, 0.3625F, 0.58F, new CubeDeformation(0.0F))
		.texOffs(217, 150).addBox(-1.7029F, -0.5437F, -0.29F, 3.4057F, 0.3625F, 0.58F, new CubeDeformation(0.0F))
		.texOffs(201, 150).addBox(-1.7029F, -0.9063F, -0.29F, 3.4057F, 0.3625F, 0.58F, new CubeDeformation(0.0F))
		.texOffs(186, 150).addBox(-1.6086F, -1.2688F, -0.29F, 3.2172F, 0.3625F, 0.58F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.62F, 11.2188F, 0.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition faceted_joint_ring_49_r2 = left_arm.addOrReplaceChild("faceted_joint_ring_49_r2", CubeListBuilder.create().texOffs(31, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0253F, 6.3753F, -2.005F, 0.0F, 0.0F, 5.4978F));

		PartDefinition faceted_joint_ring_48_r2 = left_arm.addOrReplaceChild("faceted_joint_ring_48_r2", CubeListBuilder.create().texOffs(26, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.45F, 5.35F, -2.005F, 0.0F, 0.0F, 4.7124F));

		PartDefinition faceted_joint_ring_47_r2 = left_arm.addOrReplaceChild("faceted_joint_ring_47_r2", CubeListBuilder.create().texOffs(21, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0253F, 4.3247F, -2.005F, 0.0F, 0.0F, 3.927F));

		PartDefinition faceted_joint_ring_46_r2 = left_arm.addOrReplaceChild("faceted_joint_ring_46_r2", CubeListBuilder.create().texOffs(16, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.9F, -2.005F, 0.0F, 0.0F, 3.1416F));

		PartDefinition faceted_joint_ring_45_r2 = left_arm.addOrReplaceChild("faceted_joint_ring_45_r2", CubeListBuilder.create().texOffs(11, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0253F, 4.3247F, -2.005F, 0.0F, 0.0F, 2.3562F));

		PartDefinition faceted_joint_ring_44_r2 = left_arm.addOrReplaceChild("faceted_joint_ring_44_r2", CubeListBuilder.create().texOffs(6, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.45F, 5.35F, -2.005F, 0.0F, 0.0F, 1.5708F));

		PartDefinition faceted_joint_ring_43_r2 = left_arm.addOrReplaceChild("faceted_joint_ring_43_r2", CubeListBuilder.create().texOffs(1, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0253F, 6.3753F, -2.005F, 0.0F, 0.0F, 0.7854F));

		PartDefinition right_leg = partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(59, 127).addBox(-1.7743F, 0.0F, -2.25F, 3.5486F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(78, 127).addBox(-1.8828F, 0.5571F, -2.25F, 3.7657F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(97, 127).addBox(-1.9915F, 1.1143F, -2.25F, 3.9829F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(116, 127).addBox(-2.1F, 1.6714F, -2.25F, 4.2F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(136, 127).addBox(-1.9915F, 2.2286F, -2.25F, 3.9829F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(155, 127).addBox(-1.8828F, 2.7857F, -2.25F, 3.7657F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(174, 127).addBox(-1.7743F, 3.3429F, -2.25F, 3.5486F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(116, 186).addBox(-0.2094F, 0.4978F, -2.4F, 1.4094F, 0.1044F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(21, 162).addBox(-0.1572F, 0.55F, -2.4F, 0.1044F, 1.5834F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(122, 186).addBox(-0.2094F, 1.9768F, -2.4F, 1.305F, 0.1044F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(187, 166).addBox(1.0434F, 1.1155F, -2.4F, 0.1044F, 1.0179F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(127, 186).addBox(0.3039F, 1.0633F, -2.4F, 0.7917F, 0.1044F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(162, 177).addBox(0.3561F, 1.1155F, -2.4F, 0.1044F, 0.5133F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(94, 183).addBox(1.53F, 0.48F, -2.4F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(16, 186).addBox(1.5804F, 0.5304F, -2.46F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(97, 183).addBox(-1.77F, 0.48F, -2.4F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(19, 186).addBox(-1.7196F, 0.5304F, -2.46F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(121, 156).addBox(-1.036F, 3.664F, -2.2F, 2.072F, 2.072F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(36, 170).addBox(-0.3F, 5.88F, -2.38F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F))
		.texOffs(64, 162).addBox(-0.6F, 4.1F, -2.47F, 1.2F, 1.2F, 0.4F, new CubeDeformation(0.0F))
		.texOffs(237, 79).addBox(-1.8143F, 5.65F, -2.65F, 3.6286F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(183, 103).addBox(-1.9228F, 6.1571F, -2.65F, 3.8457F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(203, 103).addBox(-2.0315F, 6.6643F, -2.65F, 4.0629F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(224, 103).addBox(-2.14F, 7.1714F, -2.65F, 4.28F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(1, 111).addBox(-2.0315F, 7.6786F, -2.65F, 4.0629F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(22, 111).addBox(-1.9228F, 8.1857F, -2.65F, 3.8457F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(42, 111).addBox(-1.8143F, 8.6929F, -2.65F, 3.6286F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(254, 32).addBox(1.58F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(251, 42).addBox(1.03F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(254, 42).addBox(0.48F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(251, 52).addBox(-0.07F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(254, 52).addBox(-0.62F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(251, 87).addBox(-1.17F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(254, 87).addBox(-1.72F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(188, 186).addBox(0.316F, 5.958F, -2.91F, 1.134F, 0.084F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(254, 162).addBox(0.358F, 6.0F, -2.91F, 0.084F, 1.274F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(193, 186).addBox(0.316F, 7.148F, -2.91F, 1.05F, 0.084F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(250, 174).addBox(1.324F, 6.455F, -2.91F, 0.084F, 0.819F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(198, 186).addBox(0.729F, 6.413F, -2.91F, 0.637F, 0.084F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(105, 180).addBox(0.771F, 6.455F, -2.91F, 0.084F, 0.413F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(52, 150).addBox(-1.7306F, 9.0F, -1.8F, 3.4613F, 0.2667F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(69, 150).addBox(-1.8F, 9.2667F, -1.8F, 3.6F, 0.2667F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(86, 150).addBox(-1.7306F, 9.5333F, -1.8F, 3.4613F, 0.2667F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(135, 32).addBox(-2.4009F, 9.65F, -3.8F, 4.8017F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(162, 32).addBox(-2.5005F, 10.0333F, -3.8F, 5.001F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(189, 32).addBox(-2.6001F, 10.4167F, -3.8F, 5.2003F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(217, 32).addBox(-2.6001F, 10.8F, -3.8F, 5.2003F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(1, 42).addBox(-2.5005F, 11.1833F, -3.8F, 5.001F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(28, 42).addBox(-2.4009F, 11.5667F, -3.8F, 4.8017F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(227, 156).addBox(1.4F, 10.55F, -4.02F, 0.7F, 1.15F, 0.54F, new CubeDeformation(0.0F))
		.texOffs(232, 156).addBox(0.52F, 10.55F, -4.02F, 0.7F, 1.15F, 0.54F, new CubeDeformation(0.0F))
		.texOffs(237, 156).addBox(-0.36F, 10.55F, -4.02F, 0.7F, 1.15F, 0.54F, new CubeDeformation(0.0F))
		.texOffs(242, 156).addBox(-1.24F, 10.55F, -4.02F, 0.7F, 1.15F, 0.54F, new CubeDeformation(0.0F))
		.texOffs(247, 156).addBox(-2.12F, 10.55F, -4.02F, 0.7F, 1.15F, 0.54F, new CubeDeformation(0.0F))
		.texOffs(28, 183).addBox(2.06F, 10.06F, -3.98F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(222, 183).addBox(2.1188F, 10.1188F, -4.04F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(31, 183).addBox(-2.34F, 10.06F, -3.98F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(225, 183).addBox(-2.2812F, 10.1188F, -4.04F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(187, 200).addBox(-2.2F, 10.0F, 3.18F, 4.4F, 1.35F, 0.5F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, 12.0F, 0.0F));

		PartDefinition refined_toe_corner_L1_74_r1 = right_leg.addOrReplaceChild("refined_toe_corner_L1_74_r1", CubeListBuilder.create().texOffs(161, 201).addBox(-0.28F, -0.39F, -0.31F, 0.56F, 0.78F, 0.62F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.22F, 11.19F, -3.61F, 0.0F, -0.3142F, 0.0F));

		PartDefinition refined_toe_corner_L-1_73_r1 = right_leg.addOrReplaceChild("refined_toe_corner_L-1_73_r1", CubeListBuilder.create().texOffs(156, 201).addBox(-0.28F, -0.39F, -0.31F, 0.56F, 0.78F, 0.62F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.22F, 11.19F, -3.61F, 0.0F, 0.3142F, 0.0F));

		PartDefinition refined_instep_plate_L_72_r1 = right_leg.addOrReplaceChild("refined_instep_plate_L_72_r1", CubeListBuilder.create().texOffs(1, 195).addBox(-1.95F, -0.275F, -1.6F, 3.9F, 0.55F, 3.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 9.825F, -1.25F, 0.1222F, 0.0F, 0.0F));

		PartDefinition refined_greave_edge_L1_70_r1 = right_leg.addOrReplaceChild("refined_greave_edge_L1_70_r1", CubeListBuilder.create().texOffs(242, 192).addBox(-0.25F, -1.55F, -0.35F, 0.5F, 3.1F, 0.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.93F, 7.45F, -2.13F, 0.0F, 0.0F, -0.0873F));

		PartDefinition refined_knee_axle_L1_69_r1 = right_leg.addOrReplaceChild("refined_knee_axle_L1_69_r1", CubeListBuilder.create().texOffs(249, 198).addBox(-0.3F, -0.5F, -0.65F, 0.6F, 1.0F, 1.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.92F, 4.7F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition refined_thigh_side_L1_68_r1 = right_leg.addOrReplaceChild("refined_thigh_side_L1_68_r1", CubeListBuilder.create().texOffs(132, 192).addBox(-0.25F, -1.325F, -1.85F, 0.5F, 2.65F, 3.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.95F, 2.125F, 0.0F, 0.0F, 0.0F, 0.0873F));

		PartDefinition refined_greave_edge_L-1_67_r1 = right_leg.addOrReplaceChild("refined_greave_edge_L-1_67_r1", CubeListBuilder.create().texOffs(237, 192).addBox(-0.25F, -1.55F, -0.35F, 0.5F, 3.1F, 0.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.93F, 7.45F, -2.13F, 0.0F, 0.0F, 0.0873F));

		PartDefinition refined_knee_axle_L-1_66_r1 = right_leg.addOrReplaceChild("refined_knee_axle_L-1_66_r1", CubeListBuilder.create().texOffs(243, 198).addBox(-0.3F, -0.5F, -0.65F, 0.6F, 1.0F, 1.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.92F, 4.7F, 0.0F, 0.0F, 0.7854F, 0.0F));

		PartDefinition refined_thigh_side_L-1_65_r1 = right_leg.addOrReplaceChild("refined_thigh_side_L-1_65_r1", CubeListBuilder.create().texOffs(121, 192).addBox(-0.25F, -1.325F, -1.85F, 0.5F, 2.65F, 3.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.95F, 2.125F, 0.0F, 0.0F, 0.0F, -0.0873F));

		PartDefinition faceted_joint_ring_25_r1 = right_leg.addOrReplaceChild("faceted_joint_ring_25_r1", CubeListBuilder.create().texOffs(71, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0465F, 5.7465F, -2.155F, 0.0F, 0.0F, 5.4978F));

		PartDefinition faceted_joint_ring_24_r1 = right_leg.addOrReplaceChild("faceted_joint_ring_24_r1", CubeListBuilder.create().texOffs(66, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.48F, 4.7F, -2.155F, 0.0F, 0.0F, 4.7124F));

		PartDefinition faceted_joint_ring_23_r1 = right_leg.addOrReplaceChild("faceted_joint_ring_23_r1", CubeListBuilder.create().texOffs(61, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0465F, 3.6535F, -2.155F, 0.0F, 0.0F, 3.927F));

		PartDefinition faceted_joint_ring_22_r1 = right_leg.addOrReplaceChild("faceted_joint_ring_22_r1", CubeListBuilder.create().texOffs(56, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.22F, -2.155F, 0.0F, 0.0F, 3.1416F));

		PartDefinition faceted_joint_ring_21_r1 = right_leg.addOrReplaceChild("faceted_joint_ring_21_r1", CubeListBuilder.create().texOffs(51, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0465F, 3.6535F, -2.155F, 0.0F, 0.0F, 2.3562F));

		PartDefinition faceted_joint_ring_20_r1 = right_leg.addOrReplaceChild("faceted_joint_ring_20_r1", CubeListBuilder.create().texOffs(46, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.48F, 4.7F, -2.155F, 0.0F, 0.0F, 1.5708F));

		PartDefinition faceted_joint_ring_19_r1 = right_leg.addOrReplaceChild("faceted_joint_ring_19_r1", CubeListBuilder.create().texOffs(41, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0465F, 5.7465F, -2.155F, 0.0F, 0.0F, 0.7854F));

		PartDefinition left_leg = partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(193, 127).addBox(-1.7743F, 0.0F, -2.25F, 3.5486F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(212, 127).addBox(-1.8828F, 0.5571F, -2.25F, 3.7657F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(231, 127).addBox(-1.9915F, 1.1143F, -2.25F, 3.9829F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(1, 135).addBox(-2.1F, 1.6714F, -2.25F, 4.2F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(21, 135).addBox(-1.9915F, 2.2286F, -2.25F, 3.9829F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(40, 135).addBox(-1.8828F, 2.7857F, -2.25F, 3.7657F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(59, 135).addBox(-1.7743F, 3.3429F, -2.25F, 3.5486F, 0.5571F, 4.5F, new CubeDeformation(0.0F))
		.texOffs(131, 186).addBox(-0.2094F, 0.4978F, -2.4F, 1.4094F, 0.1044F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(24, 162).addBox(-0.1572F, 0.55F, -2.4F, 0.1044F, 1.5834F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(137, 186).addBox(-0.2094F, 1.9768F, -2.4F, 1.305F, 0.1044F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(190, 166).addBox(1.0434F, 1.1155F, -2.4F, 0.1044F, 1.0179F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(142, 186).addBox(0.3039F, 1.0633F, -2.4F, 0.7917F, 0.1044F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(165, 177).addBox(0.3561F, 1.1155F, -2.4F, 0.1044F, 0.5133F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(100, 183).addBox(1.53F, 0.48F, -2.4F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(22, 186).addBox(1.5804F, 0.5304F, -2.46F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(103, 183).addBox(-1.77F, 0.48F, -2.4F, 0.24F, 0.24F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(25, 186).addBox(-1.7196F, 0.5304F, -2.46F, 0.1392F, 0.1392F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(128, 156).addBox(-1.036F, 3.664F, -2.2F, 2.072F, 2.072F, 0.25F, new CubeDeformation(0.0F))
		.texOffs(76, 170).addBox(-0.3F, 5.88F, -2.38F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F))
		.texOffs(70, 162).addBox(-0.6F, 4.1F, -2.47F, 1.2F, 1.2F, 0.4F, new CubeDeformation(0.0F))
		.texOffs(62, 111).addBox(-1.8143F, 5.65F, -2.65F, 3.6286F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(82, 111).addBox(-1.9228F, 6.1571F, -2.65F, 3.8457F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(102, 111).addBox(-2.0315F, 6.6643F, -2.65F, 4.0629F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(123, 111).addBox(-2.14F, 7.1714F, -2.65F, 4.28F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(144, 111).addBox(-2.0315F, 7.6786F, -2.65F, 4.0629F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(165, 111).addBox(-1.9228F, 8.1857F, -2.65F, 3.8457F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(185, 111).addBox(-1.8143F, 8.6929F, -2.65F, 3.6286F, 0.5071F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(254, 103).addBox(1.58F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(250, 127).addBox(1.03F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(253, 127).addBox(0.48F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(55, 156).addBox(-0.07F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(58, 156).addBox(-0.62F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(61, 156).addBox(-1.17F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(64, 156).addBox(-1.72F, 6.05F, -2.82F, 0.12F, 2.8F, 0.21F, new CubeDeformation(0.0F))
		.texOffs(202, 186).addBox(0.316F, 5.958F, -2.91F, 1.134F, 0.084F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(55, 166).addBox(0.358F, 6.0F, -2.91F, 0.084F, 1.274F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(207, 186).addBox(0.316F, 7.148F, -2.91F, 1.05F, 0.084F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(253, 174).addBox(1.324F, 6.455F, -2.91F, 0.084F, 0.819F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(212, 186).addBox(0.729F, 6.413F, -2.91F, 0.637F, 0.084F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(108, 180).addBox(0.771F, 6.455F, -2.91F, 0.084F, 0.413F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(103, 150).addBox(-1.7306F, 9.0F, -1.8F, 3.4613F, 0.2667F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(120, 150).addBox(-1.8F, 9.2667F, -1.8F, 3.6F, 0.2667F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(137, 150).addBox(-1.7306F, 9.5333F, -1.8F, 3.4613F, 0.2667F, 3.6F, new CubeDeformation(0.0F))
		.texOffs(55, 42).addBox(-2.4009F, 9.65F, -3.8F, 4.8017F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(82, 42).addBox(-2.5005F, 10.0333F, -3.8F, 5.001F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(109, 42).addBox(-2.6001F, 10.4167F, -3.8F, 5.2003F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(137, 42).addBox(-2.6001F, 10.8F, -3.8F, 5.2003F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(165, 42).addBox(-2.5005F, 11.1833F, -3.8F, 5.001F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(192, 42).addBox(-2.4009F, 11.5667F, -3.8F, 4.8017F, 0.3833F, 7.4F, new CubeDeformation(0.0F))
		.texOffs(252, 156).addBox(1.4F, 10.55F, -4.02F, 0.7F, 1.15F, 0.54F, new CubeDeformation(0.0F))
		.texOffs(1, 162).addBox(0.52F, 10.55F, -4.02F, 0.7F, 1.15F, 0.54F, new CubeDeformation(0.0F))
		.texOffs(6, 162).addBox(-0.36F, 10.55F, -4.02F, 0.7F, 1.15F, 0.54F, new CubeDeformation(0.0F))
		.texOffs(11, 162).addBox(-1.24F, 10.55F, -4.02F, 0.7F, 1.15F, 0.54F, new CubeDeformation(0.0F))
		.texOffs(16, 162).addBox(-2.12F, 10.55F, -4.02F, 0.7F, 1.15F, 0.54F, new CubeDeformation(0.0F))
		.texOffs(34, 183).addBox(2.06F, 10.06F, -3.98F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(228, 183).addBox(2.1188F, 10.1188F, -4.04F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(37, 183).addBox(-2.34F, 10.06F, -3.98F, 0.28F, 0.28F, 0.1F, new CubeDeformation(0.0F))
		.texOffs(231, 183).addBox(-2.2812F, 10.1188F, -4.04F, 0.1624F, 0.1624F, 0.09F, new CubeDeformation(0.0F))
		.texOffs(199, 200).addBox(-2.2F, 10.0F, 3.18F, 4.4F, 1.35F, 0.5F, new CubeDeformation(0.0F)), PartPose.offset(-2.5F, 12.0F, 0.0F));

		PartDefinition refined_toe_corner_R1_74_r1 = left_leg.addOrReplaceChild("refined_toe_corner_R1_74_r1", CubeListBuilder.create().texOffs(171, 201).addBox(-0.28F, -0.39F, -0.31F, 0.56F, 0.78F, 0.62F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.22F, 11.19F, -3.61F, 0.0F, -0.3142F, 0.0F));

		PartDefinition refined_toe_corner_R-1_73_r1 = left_leg.addOrReplaceChild("refined_toe_corner_R-1_73_r1", CubeListBuilder.create().texOffs(166, 201).addBox(-0.28F, -0.39F, -0.31F, 0.56F, 0.78F, 0.62F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.22F, 11.19F, -3.61F, 0.0F, 0.3142F, 0.0F));

		PartDefinition refined_instep_plate_R_72_r1 = left_leg.addOrReplaceChild("refined_instep_plate_R_72_r1", CubeListBuilder.create().texOffs(18, 195).addBox(-1.95F, -0.275F, -1.6F, 3.9F, 0.55F, 3.2F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 9.825F, -1.25F, 0.1222F, 0.0F, 0.0F));

		PartDefinition refined_greave_edge_R1_70_r1 = left_leg.addOrReplaceChild("refined_greave_edge_R1_70_r1", CubeListBuilder.create().texOffs(252, 192).addBox(-0.25F, -1.55F, -0.35F, 0.5F, 3.1F, 0.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.93F, 7.45F, -2.13F, 0.0F, 0.0F, -0.0873F));

		PartDefinition refined_knee_axle_R1_69_r1 = left_leg.addOrReplaceChild("refined_knee_axle_R1_69_r1", CubeListBuilder.create().texOffs(35, 200).addBox(-0.3F, -0.5F, -0.65F, 0.6F, 1.0F, 1.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.92F, 4.7F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition refined_thigh_side_R1_68_r1 = left_leg.addOrReplaceChild("refined_thigh_side_R1_68_r1", CubeListBuilder.create().texOffs(154, 192).addBox(-0.25F, -1.325F, -1.85F, 0.5F, 2.65F, 3.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.95F, 2.125F, 0.0F, 0.0F, 0.0F, 0.0873F));

		PartDefinition refined_greave_edge_R-1_67_r1 = left_leg.addOrReplaceChild("refined_greave_edge_R-1_67_r1", CubeListBuilder.create().texOffs(247, 192).addBox(-0.25F, -1.55F, -0.35F, 0.5F, 3.1F, 0.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.93F, 7.45F, -2.13F, 0.0F, 0.0F, 0.0873F));

		PartDefinition refined_knee_axle_R-1_66_r1 = left_leg.addOrReplaceChild("refined_knee_axle_R-1_66_r1", CubeListBuilder.create().texOffs(227, 199).addBox(-0.3F, -0.5F, -0.65F, 0.6F, 1.0F, 1.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.92F, 4.7F, 0.0F, 0.0F, 0.7854F, 0.0F));

		PartDefinition refined_thigh_side_R-1_65_r1 = left_leg.addOrReplaceChild("refined_thigh_side_R-1_65_r1", CubeListBuilder.create().texOffs(143, 192).addBox(-0.25F, -1.325F, -1.85F, 0.5F, 2.65F, 3.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.95F, 2.125F, 0.0F, 0.0F, 0.0F, -0.0873F));

		PartDefinition faceted_joint_ring_25_r2 = left_leg.addOrReplaceChild("faceted_joint_ring_25_r2", CubeListBuilder.create().texOffs(111, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0465F, 5.7465F, -2.155F, 0.0F, 0.0F, 5.4978F));

		PartDefinition faceted_joint_ring_24_r2 = left_leg.addOrReplaceChild("faceted_joint_ring_24_r2", CubeListBuilder.create().texOffs(106, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.48F, 4.7F, -2.155F, 0.0F, 0.0F, 4.7124F));

		PartDefinition faceted_joint_ring_23_r2 = left_leg.addOrReplaceChild("faceted_joint_ring_23_r2", CubeListBuilder.create().texOffs(101, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0465F, 3.6535F, -2.155F, 0.0F, 0.0F, 3.927F));

		PartDefinition faceted_joint_ring_22_r2 = left_leg.addOrReplaceChild("faceted_joint_ring_22_r2", CubeListBuilder.create().texOffs(96, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.22F, -2.155F, 0.0F, 0.0F, 3.1416F));

		PartDefinition faceted_joint_ring_21_r2 = left_leg.addOrReplaceChild("faceted_joint_ring_21_r2", CubeListBuilder.create().texOffs(91, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0465F, 3.6535F, -2.155F, 0.0F, 0.0F, 2.3562F));

		PartDefinition faceted_joint_ring_20_r2 = left_leg.addOrReplaceChild("faceted_joint_ring_20_r2", CubeListBuilder.create().texOffs(86, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.48F, 4.7F, -2.155F, 0.0F, 0.0F, 1.5708F));

		PartDefinition faceted_joint_ring_19_r2 = left_leg.addOrReplaceChild("faceted_joint_ring_19_r2", CubeListBuilder.create().texOffs(81, 170).addBox(-0.3F, -0.3F, -0.225F, 0.6F, 0.6F, 0.45F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0465F, 5.7465F, -2.155F, 0.0F, 0.0F, 0.7854F));

		return LayerDefinition.create(meshdefinition, 256, 256);
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