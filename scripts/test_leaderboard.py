import json
import unittest
from update_leaderboard import parse_issue,tables,RULES

class LeaderboardTest(unittest.TestCase):
    def setUp(self):
        self.result=dict(attemptId='f59a24ec-8116-444d-9e8d-7cbe07dd5927',nickname='ChunkTester',activeMillis=1000,totalMillis=2500,rules=RULES,status='verified')
        self.issue=dict(number=1,title='ChunkSpeedrun result '+self.result['attemptId'],body='```json\n'+json.dumps(self.result)+'\n```')
    def test_client_cannot_verify(self):
        row=parse_issue(self.issue)
        self.assertNotIn('status',row)
        board=tables({row['attemptId']:row},{})
        self.assertEqual(board['verified'],[])
        self.assertEqual(board['unverified'][0]['status'],'unverified')
    def test_authoritative_confirmation(self):
        row=parse_issue(self.issue)
        board=tables({row['attemptId']:{**row,'activeMillis':1}},{'verified':[row]})
        self.assertEqual(board['verified'][0]['activeMillis'],1000)
        self.assertEqual(board['unverified'],[])
        self.assertEqual(tables({row['attemptId']:row},{'testOnly':[row['attemptId']]})['unverified'],[])
    def test_reject_bad_payload(self):
        for patch in ({'activeMillis':True},{'totalMillis':0},{'nickname':'bad\nname'},{'rules':'other'}):
            issue={**self.issue,'body':'```json\n'+json.dumps({**self.result,**patch})+'\n```'}
            self.assertIsNone(parse_issue(issue))
        self.assertIsNone(parse_issue({**self.issue,'body':'$(echo hacked)'}))
        self.assertIsNone(parse_issue({**self.issue,'title':'Other title'}))

if __name__=='__main__': unittest.main()
