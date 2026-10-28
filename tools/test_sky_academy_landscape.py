"""Verify the authored overlay, including the natural fringe approved on Oct 26."""
import unittest
import json
import numpy as np
from build_sky_academy import Garden, OUT, ROOT

class LandscapeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.g=Garden()
        out=np.load(OUT/'scene.npz');cls.after=out['scene'];cls.names=out['names'].tolist();cls.height=out['height']
        cls.report=json.loads((OUT/'build.json').read_text())

    def test_current_casino_and_its_approach_are_reserved(self):
        # The casino was added after the cached September terrain baseline.
        self.assertTrue(self.g.protected[227:313,59:130].all())
        self.assertTrue(np.array_equal(self.g.before[:,227:313,59:130],self.after[:,227:313,59:130]))
        for plot in json.loads((ROOT/'src/main/resources/data/tnc/housing/catalog.json').read_text('utf-8'))['plots']:
            if not plot['id'].startswith('sky_'):continue
            x,_,z=plot['min'];xx,_,zz=plot['max']
            self.assertFalse(x<=129 and xx>=59 and z<=312 and zz>=227,plot['id'])

    def test_new_land_has_ground_under_every_buildable_column(self):
        for plot in json.loads((ROOT/'src/main/resources/data/tnc/housing/catalog.json').read_text('utf-8'))['plots']:
            if not plot['id'].startswith('sky_'):continue
            x,_,z=plot['min'];xx,_,zz=plot['max']
            self.assertGreaterEqual(int(self.g.original_height[z:zz+1,x:xx+1].min()),55,plot['id'])
            self.assertFalse(self.g.protected[z:zz+1,x:xx+1].any(),plot['id'])

    def test_architecture_reservations_and_property_are_untouched(self):
        self.assertTrue(np.array_equal(self.g.before[:,self.g.protected],self.after[:,self.g.protected]))
        self.assertTrue(np.array_equal(self.g.before[:,self.g.architecture],self.after[:,self.g.architecture]))

    def test_all_four_old_cuts_are_reworked_on_the_inside(self):
        changed=np.any(self.g.before!=self.after,axis=0)
        for area in ((slice(100,420),slice(120,145)),(slice(100,420),slice(376,401)),
                     (slice(92,117),slice(125,395)),(slice(404,429),slice(125,395))):
            self.assertGreater(np.count_nonzero(changed[area]),120)

    def test_fringe_does_not_clear_an_unknown_or_manufactured_column(self):
        self.assertFalse(np.any(self.g.fringe&self.g.architecture))
        self.assertGreater(self.report['natural_fringe_columns'],10000)
        self.assertGreaterEqual(len(self.report['fringe_groves']),8)

    def test_border_foliage_is_thinned_instead_of_moved_to_another_straight_row(self):
        foliage=np.array(['_leaves' in n or '_log[' in n for n in self.names])
        z,x=np.indices((520,520));distance=np.minimum.reduce([abs(x-120),abs(x-400),abs(z-92),abs(z-428)])
        band=self.g.fringe&(distance<10)
        visible=np.arange(220)[:,None]>self.g.original_height[band][None,:]
        original=foliage[self.g.before[:,band]]&visible
        before=np.count_nonzero(original)
        # Count surviving original trees; deliberately replanted scattered groves are welcome.
        after=np.count_nonzero(original&(self.g.before[:,band]==self.after[:,band]))
        self.assertGreater(before,1000)
        self.assertLess(after,before*.5)

    def test_authored_paths_have_headroom_and_no_multi_block_step(self):
        for nodes in self.report['routes']:
            previous=None
            for a,b in zip(nodes,nodes[1:]):
                length=max(abs(a[0]-b[0]),abs(a[2]-b[2]))
                self.assertLessEqual(abs(a[1]-b[1]),length)
                for t in np.linspace(0,1,length*3+1):
                    x,y,z=np.rint(np.array(a)*(1-t)+np.array(b)*t).astype(int)
                    actual=int(self.height[z,x])
                    if previous and (x,z)!=(previous[0],previous[2]):self.assertLessEqual(abs(actual-previous[1]),1,(previous,(x,actual,z)))
                    previous=(x,actual,z)
                    if self.g.protected[z,x]:continue
                    self.assertLessEqual(abs(actual-y),1,(x,z,actual,y))
                    for yy in (actual+1,actual+2):
                        self.assertEqual(self.names[self.after[yy,z,x]],'minecraft:air',(x,yy,z))

if __name__=='__main__':unittest.main()
